#!/usr/bin/env python3
"""
ANIKOTO — Full Video Chain Analyzer (v2 — fixed parser + Kiwi mapper path)
=========================================================================
Walks the COMPLETE chain for a given episode URL, covering ALL 5 servers:
  - VidPlay-1, HD-1, Vidstream-2, VidCloud-1  (from /ajax/server/list)
  - Kiwi-Stream                                (from mapper.nekostream.site API)

For each (server × audio) combo, resolves the full chain:
  primary servers: resolve → iframe → data-id → getSourcesNew → master m3u8 → variants
  Kiwi-Stream:     resolve → iframe URL#fragment (base64) → decode → direct m3u8 → variants

Usage:
  python3 analyze-full-chain-v2.py <watch-url>
"""

import sys, re, json, base64, urllib.parse, urllib.request, urllib.error
import requests

BASE_URL = "https://anikototv.to"
MAPPER_URL = "https://mapper.nekostream.site"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

def http_get(url, headers=None, timeout=30):
    h = {"User-Agent": UA}
    if headers: h.update(headers)
    r = requests.get(url, headers=h, allow_redirects=True, timeout=timeout)
    return r.status_code, r.url, r.content, r.headers.get("content-type", "")

def rc4(key, data):
    S = list(range(256)); j = 0; kl = len(key)
    for i in range(256):
        j = (S[i] + j + key[i % kl]) % 256; S[i], S[j] = S[j], S[i]
    out = bytearray(); i = j = 0
    for c in data:
        i = (i + 1) % 256; j = (S[i] + j) % 256; S[i], S[j] = S[j], S[i]
        out.append(c ^ S[(S[i] + S[j]) % 256])
    return bytes(out)

def encode_vrf(aid):
    return urllib.parse.quote(base64.b64encode(rc4(b"simple-hash", aid.encode("iso-8859-1"))).decode("ascii"), safe="")

def ajax_headers(slug):
    return {"Referer": f"{BASE_URL}/watch/{slug}/ep-1", "X-Requested-With": "XMLHttpRequest", "Accept": "*/*", "Accept-Language": "en-US,en;q=0.9"}

def vidtube_api_headers():
    return {"Referer": "https://vidtube.site/", "X-Requested-With": "XMLHttpRequest", "Accept": "*/*", "Accept-Language": "en-US,en;q=0.9"}

def get_anime_id(slug):
    code, _, body, _ = http_get(f"{BASE_URL}/watch/{slug}/ep-1", headers={"Referer": f"{BASE_URL}/"})
    if code != 200: return None, f"HTTP {code}"
    m = re.search(r'id="watch-main"[^>]*data-id="([^"]*)"', body.decode("utf-8","replace"))
    return (m.group(1) if m else None), "ok" if m else "no data-id"

def get_episode_list(anime_id, slug):
    vrf = encode_vrf(anime_id)
    code, _, body, _ = http_get(f"{BASE_URL}/ajax/episode/list/{anime_id}?vrf={vrf}&style=default", headers=ajax_headers(slug))
    if code != 200: return None, f"HTTP {code}"
    try: return json.loads(body).get("result",""), "ok"
    except Exception as e: return None, str(e)

def parse_episodes(html):
    eps = []
    for m in re.finditer(r'<a[^>]*data-id="([^"]*)"[^>]*data-num="([^"]*)"[^>]*data-slug="([^"]*)"[^>]*data-mal="([^"]*)"[^>]*data-timestamp="([^"]*)"[^>]*data-sub="([^"]*)"[^>]*data-dub="([^"]*)"[^>]*data-ids="([^"]*)"[^>]*>(.*?)</a>', html, re.DOTALL):
        tm = re.search(r'<span class="d-title"[^>]*>([^<]*)</span>', m.group(9))
        eps.append({"data_id":m.group(1),"num":m.group(2),"slug":m.group(3),"mal":m.group(4),"timestamp":m.group(5),"sub":m.group(6)=="1","dub":m.group(7)=="1","data_ids":m.group(8),"title":(tm.group(1).strip() if tm else f"Episode {m.group(2)}")})
    return eps

def get_server_list(data_ids, slug):
    code, _, body, _ = http_get(f"{BASE_URL}/ajax/server/list?servers={data_ids}", headers=ajax_headers(slug))
    if code != 200: return None, f"HTTP {code}"
    try: return json.loads(body).get("result",""), "ok"
    except Exception as e: return None, str(e)

def parse_server_list(html):
    """★ FIXED: iterate each <div class="type" data-type="...">...</ul></div> block."""
    servers = []
    # match each type block: <div class="type" data-type="X"> ... </ul> </div>
    for tm in re.finditer(r'<div class="type"[^>]*data-type="([^"]*)"[^>]*>(.*?)</ul>\s*</div>', html, re.DOTALL):
        audio_type = tm.group(1)
        block = tm.group(2)
        for lm in re.finditer(r'<li[^>]*data-link-id="([^"]*)"[^>]*>([^<]*)</li>', block):
            servers.append({"source":"primary","audio_type":audio_type,"link_id":lm.group(1),"name":lm.group(2).strip()})
    return servers

def get_mapper_kiwi(mal_id, ep_num, timestamp, slug):
    """v3 reference's Discovery B: GET mapper.nekostream.site/api/mal/<mal>/<ep>/<ts>."""
    url = f"{MAPPER_URL}/api/mal/{mal_id}/{ep_num}/{timestamp}"
    code, _, body, _ = http_get(url, headers=ajax_headers(slug))
    if code != 200: return [], f"HTTP {code}"
    try:
        d = json.loads(body)
    except Exception as e:
        return [], f"JSON parse: {e}"
    kiwis = []
    # keys ending with "-" are server entries; "Kiwi-Stream-" has sub/dub
    for key, val in d.items():
        if not key.endswith("-"): continue
        server_name = key[:-1]  # strip the dash
        if server_name != "Kiwi-Stream": continue  # only Kiwi for now
        if not isinstance(val, dict): continue
        for audio in ("sub", "dub"):
            if audio in val and isinstance(val[audio], dict) and "url" in val[audio]:
                kiwis.append({"source":"mapper","audio_type":audio,"link_id":val[audio]["url"],"name":server_name})
    return kiwis, "ok"

def resolve_server(link_id, slug):
    enc = urllib.parse.quote(link_id, safe="")
    code, _, body, _ = http_get(f"{BASE_URL}/ajax/server?get={enc}", headers=ajax_headers(slug))
    if code != 200: return None, f"HTTP {code}"
    try:
        d = json.loads(body)
        if d.get("status") != 200: return None, f"status={d.get('status')}"
        r = d.get("result") or {}
        return {"iframe_url": r.get("url",""), "skip_data": r.get("skip_data",{})}, "ok"
    except Exception as e: return None, str(e)

def extract_data_id(iframe_url):
    code, final_url, body, _ = http_get(iframe_url, headers={"Referer":"https://vidtube.site/","Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8","Accept-Language":"en-US,en;q=0.9"})
    if code != 200: return None, final_url, f"HTTP {code}"
    html = body.decode("utf-8","replace")
    m = re.search(r'data-id="([^"]*)"', html)
    extras = {}
    for attr in ["data-realid","data-mediaid","data-fileversion","cid"]:
        mm = re.search(rf'{attr}="([^"]*)"', html)
        if mm: extras[attr] = mm.group(1)
    return (m.group(1) if m else None), final_url, {"extras":extras, "html_size":len(body)}

def get_sources_new(host, data_id, audio):
    url = f"https://{host}/stream/getSourcesNew?id={data_id}&type={audio}"
    code, _, body, ct = http_get(url, headers=vidtube_api_headers())
    if code != 200: return None, f"HTTP {code} (ct={ct})"
    try: return json.loads(body), "ok"
    except Exception as e: return None, f"JSON parse: {e}; body[:200]={body[:200]}"

def parse_master(m3u8_url, referer="https://vidtube.site/"):
    code, _, body, _ = http_get(m3u8_url, headers={"Referer":referer})
    if code != 200: return None, f"HTTP {code}"
    text = body.decode("utf-8","replace")
    if not text.startswith("#EXTM3U"): return None, f"not m3u8 ({text[:40]!r})"
    base = m3u8_url.rsplit("/",1)[0] + "/"
    variants = []
    lines = text.split("\n")
    for i, line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF:"):
            nl = lines[i+1].strip() if i+1 < len(lines) else ""
            if not nl or nl.startswith("#"): continue
            bw = re.search(r"BANDWIDTH=(\d+)", line); res = re.search(r"RESOLUTION=(\d+x\d+)", line); nm = re.search(r'NAME="([^"]+)"', line)
            variants.append({"bandwidth":int(bw.group(1)) if bw else 0,"resolution":res.group(1) if res else "","name":nm.group(1) if nm else "","url":(nl if nl.startswith("http") else base+nl)})
    return variants, "ok"

def kiwi_decode_m3u8(iframe_url):
    """Kiwi flow: iframe URL has #<base64> fragment → decode → direct m3u8 URL."""
    frag = iframe_url.split("#")[1] if "#" in iframe_url else ""
    if not frag: return None, "no #fragment"
    try:
        decoded = base64.b64decode(frag).decode("utf-8")
        if not decoded.startswith("http"): return None, f"decoded not URL: {decoded[:60]}"
        return decoded, "ok"
    except Exception as e: return None, str(e)

def fetch_variant_check_segments(variant_url, referer="https://vidtube.site/"):
    code, _, body, _ = http_get(variant_url, headers={"Referer":referer})
    if code != 200: return None, f"HTTP {code}"
    text = body.decode("utf-8","replace")
    segs = re.findall(r"#EXTINF:[\d.]+,\s*(https?://[^\s]+)", text)
    hosts = {}
    for s in segs:
        h = re.match(r"https?://([^/]+)", s).group(1)
        hosts[h] = hosts.get(h, 0) + 1
    # fetch first real segment → check PNG
    png = None
    for s in segs:
        if "/segment/" in s or "nekostream" in s or "vibeplayer" in s or "mewcdn" in s:
            scode, sfinal, sbody, sct = http_get(s, headers={"Referer":referer})
            if scode == 200 and sbody:
                is_png = sbody[:4] == b"\x89PNG"
                iend = sbody.find(b"IEND")
                cut = iend+8 if iend >= 0 else -1
                ts = sbody[cut]==0x47 if (cut>=0 and cut<len(sbody)) else False
                png = {"size":len(sbody),"is_png":is_png,"iend":iend,"cut":cut,"ts_sync":ts,"ct":sct,"final_host":re.match(r"https?://([^/]+)",sfinal).group(1) if sfinal else ""}
                break
    return {"total":len(segs),"hosts":hosts,"png":png}, "ok"

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 analyze-full-chain-v2.py <watch-url>"); sys.exit(1)
    watch_url = sys.argv[1]
    m = re.search(r"/watch/([^/]+)/ep-(\d+)", watch_url)
    if not m: print(f"ERROR: bad URL {watch_url}"); sys.exit(1)
    slug, ep_num = m.group(1), m.group(2)

    print("="*80)
    print(f"ANIKOTO Full Video Chain Analysis v2 (all 5 servers)")
    print(f"URL: {watch_url}  |  Slug: {slug}  |  Episode: {ep_num}")
    print("="*80)

    # Step 1: animeId
    anime_id, err = get_anime_id(slug)
    if not anime_id: print(f"✗ animeId FAILED: {err}"); sys.exit(1)
    print(f"\n✓ animeId = {anime_id}")

    # Step 2: episode list
    ep_html, err = get_episode_list(anime_id, slug)
    if ep_html is None: print(f"✗ episode list FAILED: {err}"); sys.exit(1)
    episodes = parse_episodes(ep_html)
    target = next((e for e in episodes if e["num"] == ep_num), None)
    if not target: print(f"✗ Episode {ep_num} not found ({len(episodes)} eps)"); sys.exit(1)
    print(f"✓ Episode {ep_num}: {target['title']}  (mal={target['mal']}, ts={target['timestamp']}, sub={target['sub']}, dub={target['dub']})")

    # Step 3a: primary server list
    srv_html, err = get_server_list(target["data_ids"], slug)
    if srv_html is None: print(f"✗ server list FAILED: {err}"); sys.exit(1)
    primary_servers = parse_server_list(srv_html)
    print(f"\n✓ Primary server list: {len(primary_servers)} combos")
    for s in primary_servers:
        print(f"    [{s['audio_type']:5s}] {s['name']:15s}")

    # Step 3b: mapper Kiwi-Stream
    kiwi_servers, err = get_mapper_kiwi(target["mal"], ep_num, target["timestamp"], slug)
    print(f"\n✓ Mapper Kiwi-Stream: {len(kiwi_servers)} combos")
    for s in kiwi_servers:
        print(f"    [{s['audio_type']:5s}] {s['name']:15s}  (mapper 'sub'=H-SUB, 'dub'=A-DUB)")

    all_servers = primary_servers + kiwi_servers
    print(f"\nTOTAL: {len(all_servers)} (server × audio) combos to resolve")

    # Steps 4-8: resolve each
    results = []
    for idx, srv in enumerate(all_servers):
        audio = srv["audio_type"]; name = srv["name"]; src = srv["source"]
        # Display label: primary hsub→HSUB, mapper sub→H-SUB, mapper dub→A-DUB
        if src == "mapper":
            disp_audio = "H-SUB" if audio == "sub" else "A-DUB" if audio == "dub" else audio
        else:
            disp_audio = audio.upper()
        print(f"\n[{idx+1}/{len(all_servers)}] {name} ({disp_audio}, src={src})")
        print("-"*70)
        r = {"server":name, "audio_type":audio, "disp_audio":disp_audio, "source":src, "link_id":srv["link_id"][:60]}

        # Step 4: resolve → iframe
        resolved, err = resolve_server(srv["link_id"], slug)
        if not resolved: print(f"  ✗ resolve FAILED: {err}"); r["status"]="resolve_failed"; r["error"]=err; results.append(r); continue
        iframe = resolved["iframe_url"]
        host = re.match(r"https?://([^/]+)", iframe).group(1) if iframe else ""
        r["iframe_url"]=iframe; r["player_host"]=host; r["skip_data"]=resolved.get("skip_data",{})
        print(f"  ✓ iframe host: {host}")
        print(f"    iframe: {iframe[:90]}")

        # Kiwi has a special flow: base64 fragment → direct m3u8
        if host == "mewcdn.online" or "vibeplayer" in iframe or "mewcdn" in iframe:
            print(f"  → Kiwi flow: decode base64 fragment")
            m3u8_url, err = kiwi_decode_m3u8(iframe)
            if not m3u8_url: print(f"  ✗ Kiwi decode FAILED: {err}"); r["status"]="kiwi_decode_failed"; r["error"]=err; results.append(r); continue
            r["m3u8_url"]=m3u8_url; r["extraction_flow"]="kiwi_base64_fragment"
            print(f"  ✓ decoded m3u8: {m3u8_url}")
            # no subtitles for Kiwi (no getSourcesNew call)
            r["tracks"]=[]
            # parse master (referer: vibeplayer.site)
            variants, err = parse_master(m3u8_url, referer="https://vibeplayer.site/")
            if not variants: print(f"  ✗ master parse FAILED: {err}"); r["status"]="master_failed"; r["error"]=err; results.append(r); continue
            r["variants"]=variants
            print(f"  ✓ {len(variants)} variants: {[v['name'] for v in variants]}")
        else:
            # Primary flow: iframe → data-id → getSourcesNew → m3u8
            print(f"  → Primary flow: iframe → data-id → getSourcesNew")
            data_id, final_url, info = extract_data_id(iframe)
            if not data_id: print(f"  ✗ data-id FAILED (final={final_url})"); r["status"]="no_data_id"; results.append(r); continue
            r["data_id"]=data_id; r["iframe_extras"]=info.get("extras",{})
            print(f"  ✓ data-id = {data_id}")
            if info.get("extras"): print(f"    extras: {info['extras']}")
            # getSourcesNew — use the CORRECT audio_type for the API
            # primary servers use the data-type value directly (sub/hsub/dub)
            sources, err = get_sources_new(host, data_id, audio)
            if not sources: print(f"  ✗ getSourcesNew FAILED: {err}"); r["status"]="getsources_failed"; r["error"]=err; results.append(r); continue
            m3u8_url = (sources.get("sources") or {}).get("file","")
            tracks = sources.get("tracks") or []
            r["m3u8_url"]=m3u8_url; r["tracks"]=[{"file":t.get("file",""),"label":t.get("label",""),"kind":t.get("kind","")} for t in tracks]; r["server_field"]=sources.get("server"); r["extraction_flow"]="primary_getSourcesNew"
            if not m3u8_url.startswith("http"): print(f"  ✗ no m3u8 (file={m3u8_url!r})"); r["status"]="no_m3u8"; results.append(r); continue
            print(f"  ✓ m3u8: {m3u8_url[:80]}")
            print(f"  subtitle tracks: {len(tracks)}")
            for t in tracks[:2]: print(f"    - {t.get('label','?')} ({t.get('kind','?')})")
            if sources.get("server"): print(f"  server field: {sources['server']}")
            # parse master
            variants, err = parse_master(m3u8_url, referer=f"https://{host}/")
            if not variants: print(f"  ✗ master parse FAILED: {err}"); r["status"]="master_failed"; r["error"]=err; results.append(r); continue
            r["variants"]=variants
            print(f"  ✓ {len(variants)} variants:")
            for v in variants: print(f"    - {v['name']:6s} | {v['resolution']:10s} | {v['bandwidth']:>7d} bps")

        # Step 8: fetch one variant + check segments/PNG (only first combo per host to save time)
        already_checked = any(x["player_host"]==r.get("player_host") for x in results)
        if not already_checked and r.get("variants"):
            ref = "https://vibeplayer.site/" if "vibeplayer" in str(r.get("m3u8_url","")) else "https://vidtube.site/"
            seg_info, err = fetch_variant_check_segments(r["variants"][0]["url"], referer=ref)
            if seg_info:
                r["seg_info"]=seg_info
                print(f"  ✓ segment check: {seg_info['total']} segments, hosts={seg_info['hosts']}")
                if seg_info.get("png"):
                    p=seg_info["png"]; print(f"    PNG: size={p['size']}, is_png={p['is_png']}, IEND@{p['iend']}, cut@{p['cut']}, ts_sync={p['ts_sync']}, ct={p['ct']}, final_host={p['final_host']}")

        r["status"]="ok"
        results.append(r)

    # ─── Summary ─────────────────────────────────────────────────────────
    print("\n" + "="*80)
    print("SUMMARY: All (server × audio) combos")
    print("="*80)
    print(f"{'#':>3} {'Server':14s} {'Disp':5s} {'Source':7s} {'Player Host':20s} {'Data-ID':10s} {'Variants':22s} {'Status'}")
    print("-"*110)
    for i, r in enumerate(results):
        vlist = ",".join(v["name"] for v in r.get("variants",[])) if r.get("variants") else "-"
        mk = "✓" if r["status"]=="ok" else "✗"
        print(f"{mk}{i+1:>2} {r['server']:14s} {r.get('disp_audio',r['audio_type']):5s} {r['source']:7s} {r.get('player_host','')[:20]:20s} {r.get('data_id','')[:10]:10s} {vlist:22s} {r['status']}")

    # Save JSON
    with open("/tmp/anikoto-chain-analysis-v2.json","w") as f:
        json.dump({"watch_url":watch_url,"slug":slug,"ep_num":ep_num,"anime_id":anime_id,"episode_title":target["title"],"results":results}, f, indent=2)
    print(f"\nFull JSON: /tmp/anikoto-chain-analysis-v2.json")

if __name__ == "__main__":
    main()
