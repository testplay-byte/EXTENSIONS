# ANIKOTO — Extraction Flows (2 distinct paths)

> Last updated: 2026-06-23 (session 12) · Status: ✅ VERIFIED
> Companion to `server-audio-resolution-matrix.md`

There are **two completely different extraction flows** depending on the server. Our extension
must implement both.

## Flow A — Primary (VidPlay-1, HD-1, Vidstream-2, VidCloud-1)

```
EpisodeMeta (from SEpisode.url)
  │  data_ids = base64 blob from episode <a data-ids>
  │
  ▼
GET /ajax/server/list?servers=<data_ids>
  │  headers: ajaxHeaders(slug)
  │  → JSON {status:200, result: HTML}
  │
  ▼  parse HTML
div.servers > div.type[data-type="sub|hsub|dub"] > ul > li[data-link-id="<token>"]<ServerName>
  │
  │  for each (server × audio_type):
  ▼
GET /ajax/server?get=<URLEncode(link_id)>
  │  headers: ajaxHeaders(slug)
  │  → JSON {status:200, result: {url: <iframe_url>, skip_data: {intro:[s,e], outro:[s,e]}}}
  │
  ▼  check iframe host
iframe_url = result.url
  │  player_host = extract host from iframe_url
  │
  ├── if vidtube.site   → VidPlay-1 flow (continue below)
  ├── if megaplay.buzz  → HD-1 (path s-5) or Vidstream-2 (path s-2) flow (continue below)
  ├── if vidwish.live   → VidCloud-1 → SKIP (getSourcesNew broken)
  └── if mewcdn.online  → Kiwi flow (see Flow B)
  │
  ▼  GET iframe page
GET <iframe_url>
  │  headers: vidtubePageHeaders() (UA, Referer=vidtube.site, Accept=text/html)
  │  → HTML
  │
  ▼  extract data-id
data-id = regex `data-id="(\d+)"` from HTML
  │  (also: data-realid, data-mediaid, data-fileversion, cid — not needed for extraction)
  │
  ▼  call getSourcesNew
GET https://<player_host>/stream/getSourcesNew?id=<data-id>&type=<audio_type>
  │  headers: vidtubeApiHeaders() (UA, Referer=vidtube.site, X-Requested-With=XMLHttpRequest)
  │  → JSON {sources: {file: <master_m3u8_url>}, tracks: [{file, label, kind}], server: 5}
  │
  ▼  parse master m3u8
GET <master_m3u8_url>
  │  headers: {Referer: https://<player_host>/, UA: Mozilla/5.0}
  │  → #EXTM3U + #EXT-X-STREAM-INF lines
  │  parse variants: List<VariantInfo(url, bandwidth, resolution, name)>
  │
  ▼  for each variant: fetch media playlist
GET <variant_url>
  │  headers: {Referer: https://<player_host>/, UA: Mozilla/5.0}
  │  → #EXTINF:<dur>,<segment_url> pairs
  │  parseVariantSegments → List<SegmentInfo(url, duration)>
  │  ★ AD FILTERING: keep only segments on the real CDN host (nekostream.site),
  │    drop segments on ad CDN hosts (ipstatp.com, ibyteimg.com)
  │    (see ad-filtering-strategy.md)
  │
  ▼  build AudioStream
AudioStream(
  audioType = "sub"|"hsub"|"dub",
  audioLabel = "SUB"|"HSUB"|"DUB",
  hosterName = "<ServerName>",         // e.g. "VidPlay-1", "HD-1", "Vidstream-2"
  variants = [VariantData(quality, bandwidth, resolution, segments)],
  subtitles = [SubtitleData(url, label, lang)]   // from tracks; lang inferred from label
)
```

### Flow A — data-id patterns (the "safe approach")

**VidPlay-1** (vidtube.site): ONE data-id for ALL 3 audio types.
- The `type=sub|hsub|dub` param in `getSourcesNew` differentiates the audio.
- Only ONE iframe fetch needed → 3 `getSourcesNew` calls with different `type` values.
- This is the efficient "safe approach".

**HD-1 / Vidstream-2** (megaplay.buzz): DIFFERENT data-id per audio type.
- `data-id = 176012` (SUB), `176261` (HSUB), `176502` (DUB) — each audio has its own iframe page.
- 3 iframe fetches needed (one per audio type) → 3 `getSourcesNew` calls.
- HD-1 and Vidstream-2 share the SAME data-ids (token sharing — see dedup-strategy.md).

### Flow A — player-host-specific notes

| Player host | Path prefix | Server | Notes |
|-------------|-------------|--------|-------|
| `vidtube.site` | `/stream/<token>/<audio>` | VidPlay-1 | Clean iframe, data-id is a simple integer |
| `megaplay.buzz` | `/stream/s-5/<realid>/<audio>` | HD-1 | `s-5` prefix, data-id from `data-id` attr |
| `megaplay.buzz` | `/stream/s-2/<realid>/<audio>` | Vidstream-2 | `s-2` prefix, same data-id as HD-1 (token sharing) |
| `vidwish.live` | `/stream/s-2/<realid>/<audio>` | VidCloud-1 | iframe loads, data-id extractable, BUT `getSourcesNew` returns HTML error → SKIP |

The `getSourcesNew` endpoint is always `https://<player_host>/stream/getSourcesNew?id=<data-id>&type=<audio>` — same path on all 3 working hosts (vidtube.site, megaplay.buzz). Only vidwish.live's version is broken.

---

## Flow B — Kiwi-Stream (mapper API + base64 fragment)

```
EpisodeMeta (from SEpisode.url)
  │  malId, epNum, timestamp = from EpisodeMeta
  │
  ▼
GET https://mapper.nekostream.site/api/mal/<malId>/<epNum>/<timestamp>
  │  headers: ajaxHeaders(slug)
  │  → JSON {
  │      "Kiwi-Stream-": {
  │        "sub": {"url": "<base64-token>"},
  │        "dub": {"url": "<base64-token>"}
  │      },
  │      "Kiwi-Stream": {"sub": {"download": {"Kiwi-Stream": "<pahe-download-url>"}}},
  │      "status": {"serves_from": "cache", "cache_expires_in": "1 hours 33 min"}
  │    }
  │
  │  parseMapperResponse:
  │  - iterate keys ending with "-" → server entries
  │  - "Kiwi-Stream-" → serverName = "Kiwi-Stream"
  │  - for each audio in ("sub", "dub"): extract url field
  │  - mapper "sub" = H-SUB, mapper "dub" = A-DUB (per user clarification)
  │
  ▼  for each (Kiwi-Stream × audio):
GET /ajax/server?get=<URLEncode(mapper_token)>
  │  headers: ajaxHeaders(slug)
  │  → JSON {status:200, result: {url: <kiwi_iframe_url>, skip_data: {...}}}
  │
  │  kiwi_iframe_url = "https://mewcdn.online/player/plyr.php#<base64-encoded-m3u8-url>"
  │
  ▼  ★ KIWI-SPECIFIC: decode base64 fragment (NO data-id, NO getSourcesNew)
fragment = kiwi_iframe_url.split("#")[1]
master_m3u8_url = Base64.decode(fragment)
  │  → "https://vibeplayer.site/public/stream/<hash>/master.m3u8"
  │
  ▼  parse master m3u8
GET <master_m3u8_url>
  │  headers: {Referer: https://vibeplayer.site/, UA: Mozilla/5.0}
  │  → #EXTM3U + #EXT-X-STREAM-INF lines
  │  parse variants: List<VariantInfo(url, bandwidth, resolution, name)>
  │  (3 variants: 360p/720p/1080p, same bandwidths as VidPlay-1)
  │
  ▼  for each variant: fetch media playlist
GET <variant_url>
  │  headers: {Referer: https://vibeplayer.site/, UA: Mozilla/5.0}
  │  → #EXTINF:<dur>,<segment_url> pairs
  │  parseVariantSegments → List<SegmentInfo(url, duration)>
  │  ★ NO AD FILTERING for Kiwi — all segments on p16-ad-sg.ibyteimg.com
  │    (host-based filtering would remove everything; see ad-filtering-strategy.md)
  │
  ▼  build AudioStream
AudioStream(
  audioType = "sub"|"dub",                    // from mapper key
  audioLabel = "H-SUB"|"A-DUB",               // ★ Kiwi labels (normalize to HSUB/DUB?)
  hosterName = "Kiwi-Stream",
  variants = [VariantData(quality, bandwidth, resolution, segments)],
  subtitles = []                              // no getSourcesNew → no tracks
)
```

### Flow B — why it's different

1. **No `data-id`**: the m3u8 URL is directly encoded in the iframe URL's fragment (after `#`).
2. **No `getSourcesNew` call**: the base64 fragment IS the m3u8 URL — no API call needed.
3. **Different player host**: `mewcdn.online` (iframe) → `vibeplayer.site` (m3u8 + segments).
4. **Different Referer**: `https://vibeplayer.site/` (not `https://vidtube.site/`).
5. **No subtitle tracks**: since there's no `getSourcesNew` call, no `tracks` array is returned.
6. **All segments on the ad CDN**: `p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/` — cannot filter by host.

### Flow B — mapper API response structure

```json
{
  "Kiwi-Stream-": {                    // ← trailing "-" = server entry
    "sub": {                           // ← mapper "sub" = H-SUB
      "url": "<base64-token>"          // ← pass to /ajax/server?get=<token>
    },
    "dub": {                           // ← mapper "dub" = A-DUB
      "url": "<base64-token>"
    }
  },
  "Kiwi-Stream": {                     // ← no trailing "-" = download entry (NOT a stream)
    "sub": {
      "download": {
        "Kiwi-Stream": "https://pahe.nekostream.site/qKDie"
      }
    }
  },
  "status": {
    "serves_from": "cache",
    "time": 1782204392,
    "cache_expires_in": "1 hours 33 minutes 18 seconds"
  }
}
```

★ Only keys ending with `"-"` are stream entries. The `"Kiwi-Stream"` (no dash) key is a download link (pahe) — ignore it. The v3 reference's `parseMapperResponse` correctly filters by `endsWith("-")`.

### Flow B — mapper caching

The mapper API response includes `"cache_expires_in": "1 hours 33 minutes"`. Our extension can cache the mapper response for ~1 hour per (malId, epNum, timestamp) tuple to avoid redundant API calls when the user switches between H-SUB and A-DUB on the same episode.

---

## Implementation plan (for our Anikoto.kt Stage 4)

### Step 1: Discovery (get all HosterTasks)
```kotlin
// Discovery A: primary server list
val primaryUrl = "$baseUrl/ajax/server/list?servers=${meta.dataIds}"
val primaryResp = client.get(primaryUrl, ajaxHeaders(meta.slug))
val pHtml = json.decodeFromStream(ServerListResponse.serializer(), primaryResp.body.byteStream()).result
val pDoc = Jsoup.parse(pHtml)
val tasks = mutableListOf<HosterTask>()
for (typeDiv in pDoc.select("div.servers > div.type")) {
    val audioType = typeDiv.attr("data-type")  // sub|hsub|dub
    for (li in typeDiv.select("li[data-link-id]")) {
        tasks.add(HosterTask(
            label = "${audioType.uppercase()} - ${li.text()}",
            token = li.attr("data-link-id"),
            audioType = audioType,
            source = "primary"
        ))
    }
}

// Discovery B: mapper API (Kiwi-Stream)
if (meta.malId.isNotEmpty() && meta.epNum.isNotEmpty() && meta.timestamp.isNotEmpty()) {
    val mapperUrl = "https://mapper.nekostream.site/api/mal/${meta.malId}/${meta.epNum}/${meta.timestamp}"
    try {
        val mapperResp = client.get(mapperUrl, ajaxHeaders(meta.slug))
        val mapperJson = json.parseToJsonElement(mapperResp.body.string()).jsonObject
        for ((key, value) in mapperJson) {
            if (!key.endsWith("-")) continue
            val serverName = key.removeSuffix("-")
            if (serverName != "Kiwi-Stream") continue  // only Kiwi for now
            val serverObj = value.jsonObject
            for (audio in listOf("sub", "dub")) {
                val url = serverObj[audio]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                if (url != null) {
                    val dispAudio = if (audio == "sub") "H-SUB" else "A-DUB"
                    tasks.add(HosterTask("$dispAudio - $serverName", url, audio, "mapper"))
                }
            }
        }
    } catch (e: Exception) { loge("mapper FAILED", e) }
}
```

### Step 2: Parallel resolution (dispatch by source/host)
```kotlin
val resolvedStreams = coroutineScope {
    tasks.map { task ->
        async {
            try {
                val resolved = resolveServer(task.token, meta.slug) ?: return@async null
                val iframeUrl = resolved.iframeUrl
                val host = iframeUrl.toHttpUrl().host
                when {
                    // Flow A: primary servers (vidtube.site, megaplay.buzz)
                    host.contains("vidtube.site") || host.contains("megaplay.buzz") ->
                        resolveVidTubeStream(iframeUrl, task.audioType, task.label)
                    // Flow B: Kiwi (mewcdn.online → base64 fragment)
                    host.contains("mewcdn.online") ->
                        resolveKiwiStream(iframeUrl, task.audioType, task.label)
                    // Skip broken/unknown hosts (vidwish.live = VidCloud-1)
                    else -> { loge("UNKNOWN host: $host"); null }
                }
            } catch (e: Exception) { loge("resolve FAILED: ${task.label}", e); null }
        }
    }.awaitAll()
}.filterNotNull()
```

### Step 3: resolveVidTubeStream (Flow A)
```kotlin
suspend fun resolveVidTubeStream(iframeUrl: String, audioType: String, hosterName: String): AudioStream? {
    val host = iframeUrl.toHttpUrl().host
    // 1. GET iframe → extract data-id
    val pageHtml = noCloudflareClient.newCall(GET(iframeUrl, vidtubePageHeaders())).execute().body.string()
    val dataId = Regex("data-id=\"(\\d+)\"").find(pageHtml)?.groupValues?.get(1) ?: return null
    // 2. GET getSourcesNew?id=<data-id>&type=<audioType>
    val sourcesUrl = "https://$host/stream/getSourcesNew?id=$dataId&type=$audioType"
    val sourcesResp = noCloudflareClient.newCall(GET(sourcesUrl, vidtubeApiHeaders())).execute()
    val sources = json.decodeFromString(VidTubeSourcesResponse.serializer(), sourcesResp.body.string())
    val masterM3u8 = sources.sources?.file?.takeIf { it.startsWith("http") } ?: return null
    // 3. Parse master → variants
    val masterText = noCloudflareClient.newCall(GET(masterM3u8, segHeaders(host))).execute().body.string()
    val variants = parseMasterPlaylist(masterText, masterM3u8)
    // 4. For each variant: fetch + parse segments (with ad filtering)
    val variantData = variants.map { v ->
        val varText = noCloudflareClient.newCall(GET(v.url, segHeaders(host))).execute().body.string()
        VariantData(v.name, v.bandwidth, v.resolution, parseVariantSegments(varText, v.url, filterAds = true))
    }
    // 5. Build subtitles
    val subtitles = sources.tracks.map { track -> SubtitleData(track.file, track.label, inferLang(track.label)) }
    return AudioStream(audioType, audioType.uppercase(), hosterName, variantData, subtitles)
}
```

### Step 4: resolveKiwiStream (Flow B)
```kotlin
suspend fun resolveKiwiStream(iframeUrl: String, audioType: String, hosterName: String): AudioStream? {
    // 1. Decode base64 fragment → direct m3u8 URL
    val fragment = iframeUrl.substringAfter("#")
    val masterM3u8 = try { Base64.decode(fragment, Base64.DEFAULT).toString(Charsets.ISO_8859_1) }
                      catch (e: Exception) { return null }
    if (!masterM3u8.startsWith("http")) return null
    // 2. Parse master → variants (Referer: vibeplayer.site)
    val masterText = noCloudflareClient.newCall(GET(masterM3u8, kiwiHeaders())).execute().body.string()
    val variants = parseMasterPlaylist(masterText, masterM3u8)
    // 3. For each variant: fetch + parse segments (NO ad filtering for Kiwi)
    val variantData = variants.map { v ->
        val varText = noCloudflareClient.newCall(GET(v.url, kiwiHeaders())).execute().body.string()
        VariantData(v.name, v.bandwidth, v.resolution, parseVariantSegments(varText, v.url, filterAds = false))
    }
    // 4. No subtitles for Kiwi (no getSourcesNew call)
    val dispAudio = if (audioType == "sub") "H-SUB" else "A-DUB"
    return AudioStream(audioType, dispAudio, hosterName, variantData, emptyList())
}
```

### Step 5: parseVariantSegments (with optional ad filtering)
```kotlin
fun parseVariantSegments(text: String, variantUrl: String, filterAds: Boolean): List<SegmentInfo> {
    val base = variantUrl.substringBeforeLast("/") + "/"
    val segments = mutableListOf<SegmentInfo>()
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        if (lines[i].startsWith("#EXTINF:")) {
            val duration = lines[i].substringAfter("#EXTINF:").substringBefore(",").toDoubleOrNull() ?: 0.0
            val nextLine = lines.getOrNull(i + 1)?.trim() ?: ""
            if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                val url = if (nextLine.startsWith("http")) nextLine else base + nextLine
                if (!filterAds || isRealSegment(url)) {  // ★ ad filter only for primary servers
                    segments.add(SegmentInfo(url, duration))
                }
                i += 2
            } else { i++ }
        } else { i++ }
    }
    return segments
}

fun isRealSegment(url: String): Boolean {
    // Keep nekostream.site (real CDN), drop ipstatp.com + ibyteimg.com (ad CDN)
    return url.contains("nekostream.site")
}
```

### Header sets
```kotlin
fun vidtubePageHeaders() = Headers.of(
    "User-Agent", "Mozilla/5.0",
    "Referer", "https://vidtube.site/",
    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language", "en-US,en;q=0.9"
)
fun vidtubeApiHeaders() = Headers.of(
    "User-Agent", "Mozilla/5.0",
    "Referer", "https://vidtube.site/",
    "X-Requested-With", "XMLHttpRequest",
    "Accept", "*/*",
    "Accept-Language", "en-US,en;q=0.9"
)
fun segHeaders(host: String) = Headers.of(
    "User-Agent", "Mozilla/5.0",
    "Referer", "https://$host/"
)
fun kiwiHeaders() = Headers.of(
    "User-Agent", "Mozilla/5.0",
    "Referer", "https://vibeplayer.site/"
)
```

This gives us 4 working servers × 3 audio types = 12 AudioStreams (minus VidCloud-1's 2 broken + Vidstream-2 dedup with HD-1 = 9 unique streams × 3 variants = 27 Videos, vs the reference's 1 server × 3 audio × 3 variants = 9 Videos).
