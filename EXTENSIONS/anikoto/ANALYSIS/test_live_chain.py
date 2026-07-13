#!/usr/bin/env python3
"""
Live chain analysis for the two test episodes the user provided.
Goal: understand exactly how VidCloud-1 and Vidstream-2 behave on these episodes,
so we can fix the extension's extractors.

Episode 1 (SUB only, Vidstream-2 + VidCloud-1):
  https://anikototv.to/watch/the-klutzy-class-monitor-and-the-girl-with-the-short-skirt-6zabg/ep-12

Episode 2 (VidCloud-1 broken here per user):
  https://anikototv.to/watch/smoking-behind-the-supermarket-with-you/ep-5
"""
import re
import json
import sys
import requests
from urllib.parse import urlencode, quote

BASE = "https://anikototv.to"
UA = "Mozilla/5.0"
S = requests.Session()
S.headers.update({
    "User-Agent": UA,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
})

def xhr_headers(referer=f"{BASE}/"):
    return {
        "Referer": referer,
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "*/*",
        "User-Agent": UA,
    }

def get_anime_id(slug):
    """Get the anime's internal data-id from the detail page (#watch-main data-id)."""
    url = f"{BASE}/watch/{slug}/ep-1"
    r = S.get(url, timeout=30)
    r.raise_for_status()
    m = re.search(r'id="watch-main"[^>]*data-id="(\d+)"', r.text)
    if not m:
        # try alternate
        m = re.search(r'data-id="(\d+)"[^>]*id="watch-main"', r.text)
    return m.group(1) if m else None, r.text

def get_episodes(anime_id, slug):
    """Fetch /ajax/episode/list/{animeId} — returns HTML with <a data-ids data-num data-sub data-dub>."""
    url = f"{BASE}/ajax/episode/list/{anime_id}?style=default"
    r = S.get(url, headers=xhr_headers(f"{BASE}/watch/{slug}/ep-1"), timeout=30)
    r.raise_for_status()
    return r.json()

def parse_episode_meta(html):
    """Parse episode list HTML, return list of dicts with ep num + data-ids + sub/dub flags.
    Actual attr order: data-id, data-num, data-slug, data-mal, data-timestamp, data-sub, data-dub, data-ids
    Be order-independent: extract each attribute separately per <a> tag.
    """
    eps = []
    for am in re.finditer(r'<a\b[^>]*data-ids="[^"]+"[^>]*>', html):
        tag = am.group(0)
        def attr(name):
            mm = re.search(rf'{name}="([^"]*)"', tag)
            return mm.group(1) if mm else ""
        ids = attr("data-ids")
        if not ids:
            continue
        eps.append({
            "dataIds": ids,
            "num": attr("data-num"),
            "mal": attr("data-mal"),
            "timestamp": attr("data-timestamp"),
            "sub": attr("data-sub") == "1",
            "dub": attr("data-dub") == "1",
        })
    return eps

def get_server_list(data_ids, slug):
    """GET /ajax/server/list?servers=<data-ids> — returns HTML with div.type[data-type=sub|hsub|dub] > li[data-link-id]."""
    url = f"{BASE}/ajax/server/list?servers={data_ids}"
    r = S.get(url, headers=xhr_headers(f"{BASE}/watch/{slug}/ep-1"), timeout=30)
    r.raise_for_status()
    return r.json()

def parse_server_list(html):
    """Parse server list HTML → list of {audioType, serverName, linkId}."""
    from html.parser import HTMLParser
    servers = []
    # Find <div class="type" data-type="sub|hsub|dub"> ... <li data-link-id="...">Name</li> ... </div>
    # Use regex on the HTML (it's a fragment)
    for type_m in re.finditer(r'<div[^>]*class="[^"]*type[^"]*"[^>]*data-type="(sub|hsub|dub)"[^>]*>(.*?)</div>\s*(?=<div|</div>|$)', html, re.DOTALL):
        audio = type_m.group(1)
        block = type_m.group(2)
        for li_m in re.finditer(r'<li[^>]*data-link-id="([^"]+)"[^>]*>(.*?)</li>', block, re.DOTALL):
            link_id = li_m.group(1)
            name = re.sub(r'<[^>]+>', '', li_m.group(2)).strip()
            servers.append({"audioType": audio, "serverName": name, "linkId": link_id})
    return servers

def resolve_server(link_id, slug):
    """GET /ajax/server?get=<link-id> → {"status":200,"result":{"url":"<iframe>","skip_data":{...}}}."""
    url = f"{BASE}/ajax/server?get={quote(link_id, safe='')}"
    r = S.get(url, headers=xhr_headers(f"{BASE}/watch/{slug}/ep-1"), timeout=30)
    r.raise_for_status()
    return r.json()

def analyze_iframe(iframe_url):
    """Analyze an iframe URL: extract host, path, identify which player."""
    host = re.match(r'https?://([^/]+)', iframe_url)
    host = host.group(1) if host else "?"
    print(f"      iframe host: {host}")
    print(f"      iframe url:  {iframe_url[:120]}")
    return host

def fetch_iframe_page(iframe_url):
    """GET the iframe page, return (html, status)."""
    host = re.match(r'https?://([^/]+)', iframe_url).group(1)
    r = requests.get(iframe_url, headers={
        "User-Agent": UA,
        "Referer": f"https://{host}/",
        "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
    }, timeout=30, allow_redirects=True)
    return r.text, r.status_code, r.url

def find_data_id(html):
    """Extract data-id from the iframe player page HTML."""
    m = re.search(r'data-id="(\d+)"', html)
    return m.group(1) if m else None

def get_sources_new(host, data_id, audio_type):
    """GET https://<host>/stream/getSourcesNew?id=<data-id>&type=<audio> → {sources:{file}, tracks:[...]}."""
    url = f"https://{host}/stream/getSourcesNew?id={data_id}&type={audio_type}"
    r = requests.get(url, headers={
        "User-Agent": UA,
        "Referer": f"https://{host}/",
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "*/*",
    }, timeout=30)
    return r, url

def analyze_episode(slug, ep_num, label):
    print(f"\n{'='*70}")
    print(f"EPISODE: {label}")
    print(f"  URL: {BASE}/watch/{slug}/ep-{ep_num}")
    print(f"{'='*70}")

    # 1. Get anime id
    anime_id, detail_html = get_anime_id(slug)
    if not anime_id:
        print(f"  ❌ could not find anime id on detail page")
        return
    print(f"  anime id: {anime_id}")

    # 2. Get episode list
    ep_resp = get_episodes(anime_id, slug)
    if ep_resp.get("status") != 200:
        print(f"  ❌ episode list status={ep_resp.get('status')}")
        return
    eps = parse_episode_meta(ep_resp["result"])
    if not eps:
        print(f"  ❌ no episodes parsed from HTML")
        return
    print(f"  episodes: {len(eps)}")
    # find the target episode
    target = None
    for e in eps:
        if e["num"] == str(ep_num):
            target = e
            break
    if not target:
        print(f"  ❌ ep {ep_num} not found (available: {[e['num'] for e in eps[:5]]}...)")
        return
    print(f"  ep {ep_num}: dataIds={target['dataIds'][:50]}... sub={target['sub']} dub={target['dub']}")

    # 3. Get server list
    sl = get_server_list(target["dataIds"], slug)
    if sl.get("status") != 200:
        print(f"  ❌ server list status={sl.get('status')}")
        return
    servers = parse_server_list(sl["result"])
    print(f"  servers: {len(servers)}")
    for s in servers:
        print(f"    [{s['audioType']}] {s['serverName']} linkId={s['linkId'][:30]}...")

    # 4. For each server, resolve the iframe and analyze
    print(f"\n  --- Resolving each server ---")
    for s in servers:
        print(f"\n  ▶ [{s['audioType']}] {s['serverName']}")
        try:
            resolved = resolve_server(s["linkId"], slug)
            if resolved.get("status") != 200:
                print(f"    ❌ resolve status={resolved.get('status')}")
                continue
            iframe = resolved.get("result", {}).get("url", "")
            if not iframe:
                print(f"    ❌ no iframe url in response")
                continue
            host = analyze_iframe(iframe)

            # Fetch the iframe page
            page_html, status, final_url = fetch_iframe_page(iframe)
            print(f"      page fetch: HTTP {status}, final={final_url[:80]}, len={len(page_html)}")

            # If host is vidtube/megaplay/vidwish, look for data-id and test getSourcesNew
            interesting_hosts = ("vidtube.site", "megaplay.buzz", "vidwish.live", "vidwish.", "mewcdn.online")
            if any(h in host for h in interesting_hosts):
                data_id = find_data_id(page_html)
                print(f"      data-id: {data_id}")
                if data_id:
                    # Test getSourcesNew
                    gs_resp, gs_url = get_sources_new(host, data_id, s["audioType"])
                    print(f"      getSourcesNew: HTTP {gs_resp.status_code} ({gs_url[:80]})")
                    if gs_resp.status_code == 200:
                        try:
                            gj = gs_resp.json()
                            m3u8 = gj.get("sources", {}).get("file", "")
                            tracks = gj.get("tracks", [])
                            print(f"        ✅ m3u8: {m3u8[:100]}")
                            print(f"        ✅ tracks: {len(tracks)}")
                            for t in tracks[:3]:
                                print(f"           - {t.get('label','?')} ({t.get('kind','?')}): {t.get('file','')[:60]}")
                        except Exception as ex:
                            print(f"        ⚠️ JSON parse failed: {ex}")
                            print(f"        body[:200]: {gs_resp.text[:200]}")
                    else:
                        print(f"        ❌ body[:300]: {gs_resp.text[:300]}")
                else:
                    # Maybe it's a Kiwi-style base64 fragment URL
                    if "#" in iframe:
                        frag = iframe.split("#", 1)[1]
                        print(f"      has #fragment (Kiwi-style): {frag[:50]}...")
                        import base64
                        try:
                            decoded = base64.b64decode(frag).decode("iso-8859-1")
                            print(f"      decoded: {decoded[:100]}")
                        except Exception as ex:
                            print(f"      base64 decode failed: {ex}")
                    else:
                        # Dump a snippet of the page to understand structure
                        print(f"      no data-id, no fragment. page snippet:")
                        print(f"      {page_html[:400]}")
            else:
                print(f"      (host not in interesting list — skipping deep analysis)")
        except Exception as ex:
            print(f"    ❌ ERROR: {ex}")
            import traceback
            traceback.print_exc()

# ── Run the analysis on both episodes ──────────────────────────────────────
analyze_episode(
    "the-klutzy-class-monitor-and-the-girl-with-the-short-skirt-6zabg",
    12,
    "EP1: Klutzy Class Monitor EP12 (SUB only, Vidstream-2 + VidCloud-1)",
)
analyze_episode(
    "smoking-behind-the-supermarket-with-you",
    5,
    "EP2: Smoking Behind the Supermarket EP5 (VidCloud-1 broken here per user)",
)
print("\n\nDONE.")
