# Video Playback (Kwik HLS Extraction) — Implementation Guide

> **How to implement video playback for sites that use the Kwik video host (kwik.cx).** This is the
> method used by AnimePahe 180. AniKoto uses a different approach (direct server extraction) — see
> `EXTENSIONS/anikoto/MEMORY/modules/03-video-pipeline.md` for that.
>
> **Location:** `EXTENSIONS/<name>/DEV/src/.../extractor/KwikExtractor.kt` + the main source class's `getHosterList`

---

## What this feature does

Extracts playable video URLs from a Kwik-hosted video. Kwik (kwik.cx) is a video host used by
animepahe (and others). It wraps the real stream URL in obfuscated JavaScript (Dean Edwards packer).

The flow:
1. The play page has resolution buttons with `data-src` = kwik embed links
2. Each kwik embed link (kwik.cx/e/<id>) contains a packed JS script
3. The packed JS, when unpacked, reveals the video URL (m3u8 or mp4)
4. Return a `Video` pointing at the stream URL with the right Referer header

## ★ CRITICAL LESSON: use the proven JsUnpacker library, NOT a custom unpacker

Kwik uses Dean Edwards's JS packer (`eval(function(p,a,c,k,e,d){...})`). The video URL is only
visible AFTER unpacking — it's assembled from token substitutions during the unpacking process.

**DO NOT try to implement the unpacker yourself in Kotlin.** I tried 4 different custom approaches
(builds 5-8) and ALL failed because:
- Argument parsing is fragile (patterns inside the packed string confuse regexes)
- Base encoding edge cases (base 36 vs 62 vs 95)
- Token replacement order matters

**Instead, port the PROVEN `JsUnpacker` library from the reference extension.** It's at:
`SHARED/REFERENCE_HUB/anime-extensions-ref/lib/unpacker/src/keiyoushi/lib/jsunpacker/`

The library uses a single comprehensive regex that captures all 4 arguments at once:
```
\}\s*\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)
```

Then replaces `\w+` tokens in `p` with values from `k` using the `Unbaser` class (handles base 2-95).

### How to port JsUnpacker

1. Copy `JsUnpacker.kt` + `Unbaser.kt` from the reference repo
2. Change the package to match your extension
3. Use the exact same extraction pattern as the reference:

```kotlin
val script = doc.selectFirst("script:containsData(eval\\(function)")?.data()
    ?.substringAfterLast("eval(function(")
val unpacked = JsUnpacker.unpackAndCombine("eval(function($script")
return unpacked.substringAfter("const source=\\'").substringBefore("\\';")
```

### General lesson: take references, but don't trust them blindly

The reference extension is a **valuable resource** — it has proven solutions for common problems
(Cloudflare bypass, JS unpacking, DTOs, selectors). When you hit a problem:

1. **Check if the reference extension has a solution** — it probably does
2. **Port the PROVEN library/utility**, don't reimplement from scratch
3. **Adapt the package name + imports** to match your extension
4. **Test on-device** — the reference's approach is proven to work

BUT: don't trust the reference blindly. Some things may be outdated or specific to the reference's
build system. Always verify against the live site structure (via the user's HTML sharing or on-device
logcat).

## Required files

```
EXTENSIONS/<name>/DEV/src/.../<name>/
├── <Name>.kt                          ← main source (getHosterList override + sortVideos)
└── extractor/
    ├── KwikExtractor.kt               ← Kwik extraction logic (uses JsUnpacker)
    └── jsunpacker/
        ├── JsUnpacker.kt              ← Ported from keiyoushi.lib.jsunpacker
        └── Unbaser.kt                 ← Ported from keiyoushi.lib.jsunpacker
```

## ★ The ext-lib 16 pipeline: getHosterList, NOT videoListParse

**CRITICAL:** The app (Animiru/Aniyomi) uses the ext-lib 16 **NEW pipeline**:
- `getHosterList(episode)` → returns `List<Hoster>` with pre-populated `videoList`
- `hosterListParse(response)` is **NEVER CALLED** when `getHosterList` is overridden
- `videoListParse(response)` is for legacy-pipeline forks only (pre-ext-lib-16)

**You MUST override `getHosterList(episode)` (the suspend version)** to do the real extraction.
If you only override `videoListParse`, the app will show "No available videos" because
`hosterListParse` returns empty.

```kotlin
override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
    // 1. Fetch the play page (episode.url = /play/<session>/<episodeSession>)
    // 2. Parse div#resolutionMenu > button elements
    // 3. For each: extract data-src (kwik URL) + data-resolution + data-audio
    // 4. Resolve each kwik link via KwikExtractor → create a Video
    // 5. Return listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = videos))
}
```

The `NO_HOSTER_LIST` constant tells the app "don't show a server picker — show the videos directly".

## Implementation steps

### 1. Port JsUnpacker + Unbaser

Copy from `SHARED/REFERENCE_HUB/anime-extensions-ref/lib/unpacker/src/keiyoushi/lib/jsunpacker/`
into `extractor/jsunpacker/`. Change the package.

### 2. Create the KwikExtractor

```kotlin
class KwikExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val webViewFetcher: WebViewFetcher? = null,
) {
    suspend fun extractVideoUrl(kwikUrl: String, referer: String): String? {
        val html = fetchKwikHtml(kwikUrl, referer) ?: return null
        return extractSourceFromHtml(html)
    }

    private fun extractSourceFromHtml(html: String): String? {
        val doc = org.jsoup.Jsoup.parse(html)
        val script = doc.selectFirst("script:containsData(eval\\(function)")?.data()
            ?.substringAfterLast("eval(function(") ?: return null
        val unpacked = JsUnpacker.unpackAndCombine("eval(function($script") ?: return null
        return unpacked.substringAfter("const source=\\'").substringBefore("\\';")
    }
}
```

### 3. Override getHosterList in the main source

```kotlin
override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
    val playPageUrl = "$baseUrl${episode.url}"
    val response = client.newCall(GET(playPageUrl, headers)).execute()
    val doc = response.use { it.asJsoup() }

    val buttons = doc.select("div#resolutionMenu > button")
    val videos = buttons.mapNotNull { btn ->
        val kwikLink = btn.attr("data-src")
        val resolution = btn.attr("data-resolution")  // "360", "720", "1080"
        val audio = btn.attr("data-audio")            // "jpn" (sub) or "eng" (dub)
        val quality = buildQualityLabel(resolution, audio)  // "1080p (Sub)"
        kwikExtractor.getHlsVideo(kwikLink, referer = playPageUrl, quality = quality)
    }

    return listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = videos))
}
```

### 4. Add sortVideos + video playback settings

```kotlin
override fun List<Video>.sortVideos(): List<Video> {
    val preferredQuality = settings.preferredQuality
    val preferredAudio = settings.preferredAudio
    return sortedWith(
        compareByDescending<Video> { it.videoTitle.contains(preferredQuality) }
            .thenByDescending { it.videoTitle.contains(preferredAudio) }
    )
}
```

Settings: Preferred quality (1080p/720p/360p), Preferred audio (Sub/Dub), Preferred domain.

## ★ Cloudflare handling

kwik.cx is behind Cloudflare. The **inherited `client`** (from `network.client`) has a built-in
CloudflareInterceptor that handles this on-device via WebView. The logcat proves it works
(14,475 chars received from kwik.cx, no 403).

**Do NOT add a custom Cloudflare interceptor** — the inherited one works. If OkHttp gets 403
from kwik.cx (rare), fall back to `webViewFetcher.fetchText(kwikUrl)` (Chrome's TLS).

## ★ Video constructor (ext-lib 16)

Use named args. `initialized = false`:

```kotlin
Video(
    videoUrl = streamUrl,
    videoTitle = "1080p (Sub)",
    headers = videoHeaders,   // Referer: https://kwik.cx/
    initialized = false,
)
```

## ★ episode.url must be the REAL play page path

```kotlin
setUrlWithoutDomain("/play/$animeSession/$episodeSession")
```

NOT a fake path like `/watch/...` (that causes 404). The path must be valid on the site so the
base class's fetch succeeds. Fork compat still works because `/play/...` resolves to `baseUrl`.

## Quality label: use data-resolution + data-audio attributes

```kotlin
private fun buildQualityLabel(resolution: String, audio: String): String {
    val res = if (resolution.isNotBlank()) "${resolution}p" else "Unknown"
    val aud = when (audio.lowercase()) {
        "eng" -> "Dub"
        "jpn" -> "Sub"
        else -> ""
    }
    return if (aud.isNotBlank()) "$res ($aud)" else res
}
```

Do NOT parse the button text — it includes the fansub name ("SubsPlease · 1080p"). Use the
`data-resolution` and `data-audio` HTML attributes instead.

## Common issues + fixes

### Issue: "No available videos" (no logcat output from your code)
- **Cause:** you overrode `videoListParse` instead of `getHosterList`. The app uses the new pipeline.
- **Fix:** override `suspend fun getHosterList(episode: SEpisode): List<Hoster>`.

### Issue: 404 "No available videos"
- **Cause:** `episode.url` is a fake path (e.g. `/watch/...`) that doesn't exist on the site.
- **Fix:** use the real play page path: `/play/<animeSession>/<episodeSession>`.

### Issue: "could not extract m3u8 URL from packed JS"
- **Cause:** you're trying to regex the URL from the packed (obfuscated) JS. The URL is only visible
  after unpacking.
- **Fix:** use JsUnpacker.unpackAndCombine() — it unpacks the JS natively, then extract `const source=...`.

### Issue: custom unpacker produces garbled output
- **Cause:** argument parsing matched patterns inside the packed string.
- **Fix:** DON'T implement a custom unpacker. Port JsUnpacker from the reference extension.

### Issue: Cloudflare 403 from kwik.cx
- **Cause:** OkHttp's TLS fingerprint blocked (rare — the inherited client usually handles it).
- **Fix:** fall back to `webViewFetcher.fetchText(kwikUrl)` (Chrome's TLS).

## Reference implementation

- **AnimePahe 180**: `EXTENSIONS/animepahe/DEV/src/.../extractor/KwikExtractor.kt` + `AnimePahe.kt` §getHosterList
- **Reference (for the JsUnpacker library)**: `SHARED/REFERENCE_HUB/anime-extensions-ref/lib/unpacker/`
- **Reference (for the extraction pattern)**: `SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/animepahe/extractor/KwikExtractor.kt`
