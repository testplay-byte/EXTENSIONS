#!/usr/bin/env python3
"""
ANIKOTO — Full Video Chain Analyzer
====================================
Walks the complete chain for a given episode URL:
  watch page → animeId → episode list → server list → (per server×audio) resolve →
  iframe → data-id → getSourcesNew → master m3u8 → variants (resolutions)

Prints a comprehensive, honest report of what works and what fails.

Usage:
  python3 analyze-full-chain.py <watch-url>
  python3 analyze-full-chain.py https://anikototv.to/watch/wistoria-wand-and-sword-season-2-dua04/ep-5

Dependencies: requests (pip install requests) — or falls back to urllib.
"""

import sys
import re
import json
import base64
import urllib.parse
import urllib.request
import urllib.error

try:
    import requests
    HAVE_REQUESTS = True
except ImportError:
    HAVE_REQUESTS = False

# ─── Config ───────────────────────────────────────────────────────────────────
BASE_URL = "https://anikototv.to"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

# ─── HTTP helpers (requests or urllib fallback) ───────────────────────────────
def http_get(url, headers=None, allow_redirects=True, timeout=30):
    """Returns (status_code, final_url, body_bytes, content_type)."""
    h = {"User-Agent": UA}
    if headers:
        h.update(headers)
    if HAVE_REQUESTS:
        r = requests.get(url, headers=h, allow_redirects=allow_redirects, timeout=timeout)
        return r.status_code, r.url, r.content, r.headers.get("content-type", "")
    else:
        req = urllib.request.Request(url, headers=h)
        try:
            resp = urllib.request.urlopen(req, timeout=timeout)
            return resp.status, resp.url, resp.read(), resp.headers.get("content-type", "")
        except urllib.error.HTTPError as e:
            return e.code, url, e.read() if hasattr(e, "read") else b"", e.headers.get("content-type", "") if e.headers else ""

# ─── RC4 vrf (matches the reference's AnikotoRC4 exactly) ─────────────────────
def rc4(key: bytes, data: bytes) -> bytes:
    S = list(range(256))
    j = 0
    kl = len(key)
    for i in range(256):
        j = (S[i] + j + key[i % kl]) % 256
        S[i], S[j] = S[j], S[i]
    out = bytearray()
    i = j = 0
    for c in data:
        i = (i + 1) % 256
        j = (S[i] + j) % 256
        S[i], S[j] = S[j], S[i]
        k = S[(S[i] + S[j]) % 256]
        out.append(c ^ k)
    return bytes(out)

def encode_vrf(anime_id: str) -> str:
    enc = rc4(b"simple-hash", anime_id.encode("iso-8859-1"))
    return base64.b64encode(enc).decode("ascii")  # NO_WRAP

# ─── Header helpers (matching the reference) ──────────────────────────────────
def ajax_headers(slug):
    return {
        "Referer": f"{BASE_URL}/watch/{slug}/ep-1",
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "*/*",
        "Accept-Language": "en-US,en;q=0.9",
    }

def vidtube_page_headers():
    return {
        "Referer": "https://vidtube.site/",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
    }

def vidtube_api_headers():
    return {
        "Referer": "https://vidtube.site/",
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "*/*",
        "Accept-Language": "en-US,en;q=0.9",
    }

# ─── Chain steps ──────────────────────────────────────────────────────────────
def get_anime_id(slug):
    """Step 1: fetch /watch/<slug>/ep-1 → extract #watch-main[data-id]."""
    url = f"{BASE_URL}/watch/{slug}/ep-1"
    code, _, body, _ = http_get(url, headers={"Referer": f"{BASE_URL}/"})
    if code != 200:
        return None, f"HTTP {code}"
    html = body.decode("utf-8", errors="replace")
    m = re.search(r'id="watch-main"[^>]*data-id="([^"]*)"', html)
    return (m.group(1) if m else None), (html[:200] if not m else "ok")

def get_episode_list(anime_id, slug):
    """Step 2: GET /ajax/episode/list/<id>?vrf=<rc4>&style=default → episode HTML."""
    vrf = urllib.parse.quote(encode_vrf(anime_id), safe="")
    url = f"{BASE_URL}/ajax/episode/list/{anime_id}?vrf={vrf}&style=default"
    code, _, body, _ = http_get(url, headers=ajax_headers(slug))
    if code != 200:
        return None, f"HTTP {code}"
    try:
        d = json.loads(body)
        return d.get("result", ""), "ok"
    except Exception as e:
        return None, f"JSON parse error: {e}"

def parse_episodes(episode_html):
    """Parse <a data-id data-num data-slug data-mal data-timestamp data-sub data-dub data-ids>."""
    episodes = []
    for m in re.finditer(
        r'<a[^>]*data-id="([^"]*)"[^>]*data-num="([^"]*)"[^>]*data-slug="([^"]*)"[^>]*'
        r'data-mal="([^"]*)"[^>]*data-timestamp="([^"]*)"[^>]*data-sub="([^"]*)"[^>]*'
        r'data-dub="([^"]*)"[^>]*data-ids="([^"]*)"[^>]*>(.*?)</a>',
        episode_html, re.DOTALL
    ):
        title_m = re.search(r'<span class="d-title"[^>]*>([^<]*)</span>', m.group(9))
        episodes.append({
            "data_id": m.group(1),
            "num": m.group(2),
            "slug": m.group(3),
            "mal": m.group(4),
            "timestamp": m.group(5),
            "sub": m.group(6) == "1",
            "dub": m.group(7) == "1",
            "data_ids": m.group(8),
            "title": title_m.group(1).strip() if title_m else f"Episode {m.group(2)}",
        })
    return episodes

def get_server_list(data_ids, slug):
    """Step 3: GET /ajax/server/list?servers=<data-ids> → server HTML."""
    url = f"{BASE_URL}/ajax/server/list?servers={data_ids}"
    code, _, body, _ = http_get(url, headers=ajax_headers(slug))
    if code != 200:
        return None, f"HTTP {code}"
    try:
        d = json.loads(body)
        return d.get("result", ""), "ok"
    except Exception as e:
        return None, f"JSON parse error: {e}"

def parse_server_list(server_html):
    """Parse <div class="servers"><div class="type" data-type="sub|hsub|dub"><li data-link-id="...">Name</li>."""
    servers = []  # list of {audio_type, link_id, name}
    # find each type block
    for type_m in re.finditer(r'<div class="type"[^>]*data-type="([^"]*)"[^>]*>(.*?)</div>\s*</div>', server_html, re.DOTALL):
        audio_type = type_m.group(1)
        block = type_m.group(2)
        for li_m in re.finditer(r'<li[^>]*data-link-id="([^"]*)"[^>]*>([^<]*)</li>', block):
            servers.append({
                "audio_type": audio_type,
                "link_id": li_m.group(1),
                "name": li_m.group(2).strip(),
            })
    return servers

def resolve_server(link_id, slug):
    """Step 4: GET /ajax/server?get=<url-encode(link-id)> → {result:{url, skip_data}}."""
    enc = urllib.parse.quote(link_id, safe="")
    url = f"{BASE_URL}/ajax/server?get={enc}"
    code, _, body, _ = http_get(url, headers=ajax_headers(slug))
    if code != 200:
        return None, f"HTTP {code}"
    try:
        d = json.loads(body)
        if d.get("status") != 200:
            return None, f"status={d.get('status')}"
        result = d.get("result") or {}
        return {
            "iframe_url": result.get("url", ""),
            "skip_data": result.get("skip_data", {}),
        }, "ok"
    except Exception as e:
        return None, f"JSON parse error: {e}"

def get_player_data_id(iframe_url, host_hint=""):
    """Step 5: GET iframe page → extract data-id (the file ID for getSourcesNew)."""
    code, final_url, body, _ = http_get(iframe_url, headers=vidtube_page_headers())
    if code != 200:
        return None, final_url, f"HTTP {code}"
    html = body.decode("utf-8", errors="replace")
    # data-id
    m = re.search(r'data-id="([^"]*)"', html)
    data_id = m.group(1) if m else None
    # also capture data-realid, data-mediaid, data-fileversion, cid, domain2 (megaplay/vidwish extras)
    extras = {}
    for attr in ["data-realid", "data-mediaid", "data-fileversion", "cid"]:
        mm = re.search(rf'{attr}="([^"]*)"', html) or re.search(rf'{attr}[:=]\s*[\'"]([^\'"]*)', html)
        if mm:
            extras[attr] = mm.group(1)
    return data_id, final_url, {"extras": extras, "html_size": len(body)}

def get_sources_new(player_host, data_id, audio_type):
    """Step 6: GET https://<player_host>/stream/getSourcesNew?id=<data-id>&type=<audio>."""
    url = f"https://{player_host}/stream/getSourcesNew?id={data_id}&type={audio_type}"
    code, _, body, ct = http_get(url, headers=vidtube_api_headers())
    if code != 200:
        return None, f"HTTP {code} (ct={ct})"
    try:
        d = json.loads(body)
        return d, "ok"
    except Exception as e:
        return None, f"JSON parse error: {e}; body[:200]={body[:200]}"

def parse_master_m3u8(m3u8_url):
    """Step 7: fetch master m3u8 → list of variants {bandwidth, resolution, name, url}."""
    code, _, body, _ = http_get(m3u8_url, headers={"Referer": "https://vidtube.site/", "User-Agent": UA})
    if code != 200:
        return None, f"HTTP {code}"
    text = body.decode("utf-8", errors="replace")
    if not text.startswith("#EXTM3U"):
        return None, f"not m3u8 (starts with {text[:40]!r})"
    base = m3u8_url.rsplit("/", 1)[0] + "/"
    variants = []
    lines = text.split("\n")
    for i, line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF:"):
            next_line = lines[i+1].strip() if i+1 < len(lines) else ""
            if not next_line or next_line.startswith("#"):
                continue
            bw_m = re.search(r"BANDWIDTH=(\d+)", line)
            res_m = re.search(r"RESOLUTION=(\d+x\d+)", line)
            name_m = re.search(r'NAME="([^"]+)"', line)
            url = next_line if next_line.startswith("http") else base + next_line
            variants.append({
                "bandwidth": int(bw_m.group(1)) if bw_m else 0,
                "resolution": res_m.group(1) if res_m else "",
                "name": name_m.group(1) if name_m else "",
                "url": url,
            })
    return variants, "ok"

def fetch_variant_and_check_segments(variant_url):
    """Step 8: fetch a variant m3u8 → count real vs ad segments, check PNG header."""
    code, _, body, _ = http_get(variant_url, headers={"Referer": "https://vidtube.site/", "User-Agent": UA})
    if code != 200:
        return None, f"HTTP {code}"
    text = body.decode("utf-8", errors="replace")
    segs = re.findall(r"#EXTINF:[\d.]+,\s*(https?://[^\s]+)", text)
    hosts = {}
    for s in segs:
        host = re.match(r"https?://([^/]+)", s).group(1)
        hosts[host] = hosts.get(host, 0) + 1
    # fetch first real segment (follow redirects) → check PNG header
    png_check = None
    for s in segs:
        # heuristic: real segments are on mt.nekostream.site/segment/ or similar /segment/ path
        if "/segment/" in s or "nekostream" in s:
            scode, sfinal, sbody, sct = http_get(s, headers={"Referer": "https://vidtube.site/", "User-Agent": UA})
            if scode == 200 and sbody:
                is_png = sbody[:4] == b"\x89PNG"
                iend = sbody.find(b"IEND")
                cut = iend + 8 if iend >= 0 else -1
                ts_sync = sbody[cut] == 0x47 if (cut >= 0 and cut < len(sbody)) else False
                png_check = {
                    "size": len(sbody),
                    "is_png": is_png,
                    "iend_offset": iend,
                    "cut_offset": cut,
                    "ts_sync_at_cut": ts_sync,
                    "content_type": sct,
                    "final_url_host": re.match(r"https?://([^/]+)", sfinal).group(1) if sfinal else "",
                }
                break
    return {
        "total_segments": len(segs),
        "hosts": hosts,
        "png_check": png_check,
    }, "ok"

# ─── Main ─────────────────────────────────────────────────────────────────────
def main():
    if len(sys.argv) < 2:
        print("Usage: python3 analyze-full-chain.py <watch-url>")
        sys.exit(1)
    watch_url = sys.argv[1]
    # parse slug + ep from URL: /watch/<slug>/ep-<n>
    m = re.search(r"/watch/([^/]+)/ep-(\d+)", watch_url)
    if not m:
        print(f"ERROR: could not parse slug/ep from {watch_url}")
        sys.exit(1)
    slug = m.group(1)
    ep_num = m.group(2)

    print("=" * 78)
    print(f"ANIKOTO Full Video Chain Analysis")
    print(f"URL: {watch_url}")
    print(f"Slug: {slug}  |  Episode: {ep_num}")
    print("=" * 78)

    # Step 1: animeId
    print("\n── Step 1: Get animeId from watch page ──")
    anime_id, err = get_anime_id(slug)
    if not anime_id:
        print(f"  ✗ FAILED: {err}")
        sys.exit(1)
    print(f"  ✓ animeId = {anime_id}")

    # Step 2: episode list
    print("\n── Step 2: Get episode list ──")
    ep_html, err = get_episode_list(anime_id, slug)
    if ep_html is None:
        print(f"  ✗ FAILED: {err}")
        sys.exit(1)
    episodes = parse_episodes(ep_html)
    print(f"  ✓ {len(episodes)} episodes found")
    # find the target episode
    target = None
    for ep in episodes:
        if ep["num"] == ep_num:
            target = ep
            break
    if not target:
        print(f"  ✗ Episode {ep_num} not found in list!")
        print(f"    Available episodes: {[e['num'] for e in episodes[:10]]}...")
        sys.exit(1)
    print(f"  ✓ Episode {ep_num}: {target['title']}")
    print(f"    data-ids (first 50): {target['data_ids'][:50]}...")
    print(f"    has-sub={target['sub']}  has-dub={target['dub']}")

    # Step 3: server list
    print("\n── Step 3: Get server list ──")
    srv_html, err = get_server_list(target["data_ids"], slug)
    if srv_html is None:
        print(f"  ✗ FAILED: {err}")
        sys.exit(1)
    servers = parse_server_list(srv_html)
    print(f"  ✓ {len(servers)} (server × audio) combos found:")
    for s in servers:
        print(f"    [{s['audio_type']:5s}] {s['name']:15s}  link-id={s['link_id'][:40]}...")

    # Step 4-7: for each (server × audio), resolve the full chain
    print("\n── Steps 4-7: Resolve each (server × audio) combo ──")
    print("=" * 78)
    results = []
    for idx, srv in enumerate(servers):
        audio = srv["audio_type"]
        name = srv["name"]
        link_id = srv["link_id"]
        print(f"\n[{idx+1}/{len(servers)}] {name} ({audio})")
        print("-" * 60)

        r = {"server": name, "audio_type": audio, "link_id": link_id}

        # Step 4: resolve server → iframe URL
        resolved, err = resolve_server(link_id, slug)
        if not resolved:
            print(f"  ✗ resolve FAILED: {err}")
            r["status"] = "resolve_failed"
            r["error"] = err
            results.append(r)
            continue
        iframe_url = resolved["iframe_url"]
        r["iframe_url"] = iframe_url
        r["skip_data"] = resolved.get("skip_data", {})
        # extract player host
        host_m = re.match(r"https?://([^/]+)", iframe_url)
        player_host = host_m.group(1) if host_m else ""
        r["player_host"] = player_host
        print(f"  ✓ iframe: {iframe_url[:80]}...")
        print(f"  player host: {player_host}")
        if resolved.get("skip_data"):
            print(f"  skip_data: {resolved['skip_data']}")

        # Step 5: get data-id from iframe page
        data_id, final_url, info = get_player_data_id(iframe_url, player_host)
        if not data_id:
            print(f"  ✗ data-id extraction FAILED (final_url={final_url})")
            r["status"] = "no_data_id"
            results.append(r)
            continue
        r["data_id"] = data_id
        r["iframe_extras"] = info.get("extras", {})
        print(f"  ✓ data-id = {data_id}")
        if info.get("extras"):
            print(f"    extras: {info['extras']}")

        # Step 6: getSourcesNew
        sources, err = get_sources_new(player_host, data_id, audio)
        if not sources:
            print(f"  ✗ getSourcesNew FAILED: {err}")
            r["status"] = "getsources_failed"
            r["error"] = err
            results.append(r)
            continue
        master_m3u8 = (sources.get("sources") or {}).get("file", "")
        tracks = sources.get("tracks") or []
        r["master_m3u8"] = master_m3u8
        r["tracks"] = [{"file": t.get("file",""), "label": t.get("label",""), "kind": t.get("kind","")} for t in tracks]
        r["server_field"] = sources.get("server")
        if not master_m3u8.startswith("http"):
            print(f"  ✗ no valid m3u8 (file={master_m3u8!r})")
            r["status"] = "no_m3u8"
            results.append(r)
            continue
        print(f"  ✓ master m3u8: {master_m3u8[:80]}...")
        print(f"  subtitle tracks: {len(tracks)}")
        for t in tracks[:3]:
            print(f"    - {t.get('label','?')} ({t.get('kind','?')}): {t.get('file','')[:60]}")
        if sources.get("server"):
            print(f"  server field: {sources['server']}")

        # Step 7: parse master m3u8 → variants
        variants, err = parse_master_m3u8(master_m3u8)
        if not variants:
            print(f"  ✗ master m3u8 parse FAILED: {err}")
            r["status"] = "master_parse_failed"
            r["error"] = err
            results.append(r)
            continue
        r["variants"] = variants
        print(f"  ✓ {len(variants)} variants:")
        for v in variants:
            print(f"    - {v['name']:6s} | {v['resolution']:10s} | {v['bandwidth']:>7d} bps")

        # Step 8 (only for first combo per host, to save time): fetch a variant + check segments
        # We'll do this selectively in a follow-up, not for every combo

        r["status"] = "ok"
        results.append(r)

    # ─── Summary ─────────────────────────────────────────────────────────────
    print("\n" + "=" * 78)
    print("SUMMARY: All (server × audio) combos")
    print("=" * 78)
    print(f"{'#':>3}  {'Server':15s} {'Audio':5s} {'Player Host':25s} {'Data-ID':10s} {'Variants':18s} {'Status'}")
    print("-" * 100)
    for i, r in enumerate(results):
        name = r["server"]
        audio = r["audio_type"]
        host = r.get("player_host", "")[:25]
        did = r.get("data_id", "")[:10]
        if r.get("variants"):
            vlist = ",".join(v["name"] for v in r["variants"])
        else:
            vlist = "-"
        status = r["status"]
        marker = "✓" if status == "ok" else "✗"
        print(f"{marker}{i+1:>2}  {name:15s} {audio:5s} {host:25s} {did:10s} {vlist:18s} {status}")

    # Save full JSON
    out_path = "/tmp/anikoto-chain-analysis.json"
    with open(out_path, "w") as f:
        json.dump({
            "watch_url": watch_url,
            "slug": slug,
            "ep_num": ep_num,
            "anime_id": anime_id,
            "episode_title": target["title"] if target else "",
            "data_ids": target["data_ids"] if target else "",
            "servers": results,
        }, f, indent=2)
    print(f"\nFull JSON saved to: {out_path}")

if __name__ == "__main__":
    main()
