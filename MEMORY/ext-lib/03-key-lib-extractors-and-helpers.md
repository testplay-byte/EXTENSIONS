# Key `lib/` Extractors & Helpers — Reusable Building Blocks

> Last updated: 2026-06-22 · Status: VERIFIED
> Source: `SHARED/REFERENCE_HUB/anime-extensions-ref/lib/{playlistutils,cryptoaes,m3u8server,universalextractor,filemoonextractor,streamtapeextractor,vidmolyextractor}/`.
>
> ⚠️ **CRITICAL MIGRATION FLAG (verified):** These `lib/` modules are written for **ext-lib 14**.
> They use the legacy `Video(url, quality, videoUrl, …)` positional constructor (e.g.
> `playlistutils/PlaylistUtils.kt:102-109`, `universalextractor/UniversalExtractor.kt:101`,
> `streamtapeextractor/StreamTapeExtractor.kt`). That constructor is `@Deprecated(level = ERROR)`
> in ext-lib v16 (see `MEMORY/ext-lib/01-...md` §7 and `MEMORY/research/03-...discrepancy.md` §2) —
> **these libs will NOT compile against v16 as-is.** When we port them to our v16 extensions build, every
> `Video(url, quality, videoUrl, …)` call must be rewritten as `Video(videoUrl = …, videoTitle = …, …)`.
> This is a mechanical but pervasive change. See §8 below.

---

## 1. `playlistutils/` — HLS/DASH → `List<Video>` (THE most-used helper, ~113 consumers)

**Files:** `lib/playlistutils/build.gradle.kts` (deps: `:core` only) + `src/aniyomi/lib/playlistutils/PlaylistUtils.kt` (448 lines)

**Main class (L19):**
```kotlin
class PlaylistUtils(
    private val client: OkHttpClient,
    private val headers: Headers = commonEmptyHeaders,
)
```

**Purpose:** Fetch + parse a master `.m3u8` (HLS) or `.mpd` (DASH) playlist, returning one `Video` per variant stream. Also fixes broken WebVTT subtitles.

### API table (every public method)

| Method | Signature (short) | Returns |
|---|---|---|
| `extractFromHls` (1) | `(playlistUrl: String, referer: String = playlistUrl.toDefaultReferer(), masterHeaders: Headers, videoHeaders: Headers, videoNameGen: (String) -> String = { quality -> quality }, subtitleList: List<Track> = emptyList(), audioList: List<Track> = emptyList(), toStandardQuality: (String) -> String = { stnQuality(it) }): List<Video>` (L37-57) | constant-headers overload |
| `extractFromHls` (2) | `(playlistUrl, referer, masterHeadersGen: (Headers, String) -> Headers, videoHeadersGen: (Headers, String, String) -> Headers, videoNameGen, subtitleList, audioList, toStandardQuality): List<Video>` (L80-205) | per-request headers via lambdas |
| `extractFromDash` (1) | `(mpdUrl, videoNameGen, mpdHeaders, videoHeaders, referer, subtitleList, audioList, toStandardQuality): List<Video>` (L232-254) | constant-headers DASH |
| `extractFromDash` (2) | `(mpdUrl, videoNameGen, referer, mpdHeadersGen, videoHeadersGen, subtitleList, audioList, toStandardQuality): List<Video>` (L277-301) | per-request headers |
| `extractFromDash` (3) | `(mpdUrl, videoNameGen: (String, String) -> String, referer, mpdHeadersGen, videoHeadersGen, subtitleList, audioList, toStandardQuality): List<Video>` (L326-367) | `videoNameGen` takes `(quality, bandwidth)` |
| `generateMasterHeaders` | `(baseHeaders: Headers, referer: String): Headers` (L207) | adds `Accept: */*` + `Origin`/`Referer` |
| `fixSubtitles` | `(subtitleList: List<Track>): List<Track>` (L408-421) | fetches each sub, fixes illegal double-newlines, writes temp `file://` Uri |

### How `extractFromHls` works (L93-205)
1. Fetch master playlist via `client.newCall(GET(playlistUrl, masterHeaders)).execute().bodyString()`.
2. **If no `#EXT-X-STREAM-INF:`** (single-stream media playlist): short-circuit, return one `Video(playlistUrl, videoNameGen("Video"), playlistUrl, headers=masterHeaders, subtitleTracks, audioTracks)`.
3. Otherwise: regex-extract subtitles (`#EXT-X-MEDIA:TYPE=SUBTITLES…NAME="…"…URI="…"`) and audio (`TYPE=AUDIO`), prepend caller-supplied lists.
4. Split on `#EXT-X-STREAM-INF:`. For each variant: skip audio-only (codec `mp4a` only); parse `RESOLUTION=`, `BANDWIDTH=`, `CODECS=`; build streamName like `"720p (1280x720) - 2.15 MB/s"`; `UrlUtils.fixUrl(url, playlistUrl)` to resolve relative; build `Video(url, videoNameGen(streamName), videoUrl, headers=videoHeadersGen(...), subtitleTracks, audioTracks)`.
5. Sort by `BANDWIDTH` desc, return `List<Video>`.

Quality standardization (`stnQuality`, L387-391): snaps heights to nearest of `[144,240,360,480,720,1080,1440,2160]`, appends `"p"`.

### Usage example (real, `filemoonextractor/FilemoonExtractor.kt:99-104`)
```kotlin
playlistUtils.extractFromHls(
    streamUrl,
    masterHeaders = videoHeaders,
    videoHeaders = videoHeaders,
    videoNameGen = { "$prefix${it.replace("Video", quality)}p" },
)
```

**Notes:**
- Only fetches the MASTER playlist. Per-variant `.m3u8` / segments are fetched by the player (mpv), using `videoHeadersGen` output (set as `Video.headers`).
- No "give me raw variant list" / "pick a quality" — always returns one `Video` per variant. Callers filter/sort via `List<Video>.sortVideos()`.
- `fixSubtitles` writes temp files (Android cache dir); returns `Track(file://uri, lang)`.

---

## 2. `cryptoaes/` — CryptoJS-compatible AES (249 lines)

**Files:** `lib/cryptoaes/build.gradle.kts` (no extra deps) + `src/keiyoushi/lib/cryptoaes/CryptoAES.kt` (249 lines) + `Deobfuscator.kt` (74 lines)

**Main object:** `object CryptoAES` (L26). Constants: `KEY_SIZE=32`, `IV_SIZE=16`, `SALT_SIZE=8`, `HASH_CIPHER="AES/CBC/PKCS7PADDING"`, `HASH_CIPHER_ECB="AES/ECB/PKCS5PADDING"`, `KDF_DIGEST="MD5"`.

### API table

| Method | Signature | Returns | Notes |
|---|---|---|---|
| `decrypt` (passphrase) | `(cipherText: String, password: String): String` (L45-58) | plaintext or `""` on error | CryptoJS-defaults compatible. Base64-decodes, extracts OpenSSL salt (bytes 8..16), derives key+IV via EVP_BytesToKey (MD5, 1 iter, salt+password). |
| `decryptWithSalt` | `(cipherText: String, salt: String, password: String): String` (L60-78) | plaintext or `""` | Caller supplies hex-encoded salt |
| `decrypt` (raw key+IV) | `(cipherText: String, keyBytes: ByteArray, ivBytes: ByteArray): String` (L87-92) | plaintext or `""` | Base64-decodes, AES/CBC/PKCS7 with explicit key+IV |
| `encrypt` | `(plainText: String, keyBytes: ByteArray, ivBytes: ByteArray): String` (L101-106) | base64 ciphertext or `""` | Inverse of `decrypt` |
| `decryptCbcIV` | `(encryptedBase64: String, secretKey: String, isUtf8: Boolean = false): String?` (L208-230) | plaintext or `null` | If `isUtf8=true` → AES/ECB/PKCS5 (misleading name). Else: first 16 bytes = IV, rest = payload, AES/CBC/PKCS5 with `secretKey.toByteArray(UTF_8)` as key. |
| `Deobfuscator.deobfuscateJsPassword` | `(inputString: String): String` (Deobfuscator.kt:10-35) | string of digits and `.` | JSFuck-style numeric deobfuscator (`!+[]`→1-9, `+[]`→0, `()`→`.`) |

### Usage example (real, `src/en/kickassanime/.../KickAssAnimeExtractor.kt:119-132`)
```kotlin
val (encryptedData, ivhex) = response.substringAfter(":\"").substringBefore('"').replace("\\", "").split(":")
val iv = ivhex.decodeHex()
val videoObject = try {
    val decrypted = CryptoAES.decrypt(encryptedData, key, iv)
    json.decodeFromString<VideoDto>(decrypted)
} catch (e: Exception) { e.printStackTrace(); return emptyList() }
```

### Decrypt fallback chain (real, `src/es/sololatino/.../SoloLatino.kt:366-377`)
```kotlin
val link = rawLink.trim()
if (link.startsWith("http", true)) return link
CryptoAES.decryptCbcIV(link, AES_KEY)?.takeIf { it.isNotBlank() }?.let { return it }
CryptoAES.decrypt(link, AES_KEY).takeIf { it.isNotBlank() }?.let { return it }
decodeJwtLink(link)?.takeIf { it.isNotBlank() }?.let { return it }
return null
```

**Notes:**
- `decrypt(ct, password)` swallows all exceptions, returns `""` (not null) — always blank-check.
- `decryptCbcIV(isUtf8=true)` jumps to ECB mode (misleading). Don't use `isUtf8=true` unless you know the server uses ECB.
- The `decrypt(ct, password)` salt-extraction matches CryptoJS's `OpenSSL` formatter (`Salted__<8-byte-salt><ciphertext>`) — right entry point for any `CryptoJS.AES.decrypt(ct, passphrase).toString(CryptoJS.enc.Utf8)` payload.
- **No PBKDF2 / SHA-256 / HKDF** — modern CryptoJS with PBKDF2 won't decrypt with this lib.

---

## 3. `m3u8server/` — Local NanoHTTPD proxy for segment header injection / fake-header stripping

**Files:** `lib/m3u8server/build.gradle.kts` (dep: `com.github.NanoHttpd.nanohttpd:nanohttpd:-SNAPSHOT` — the only external dep across these 4 modules) + 4 Kotlin files.

### 3a. `M3u8HttpServer` — the raw NanoHTTPD server (`M3u8HttpServer.kt`, 314 lines)
```kotlin
class M3u8HttpServer(private val client: OkHttpClient, port: Int = 0) : NanoHTTPD(port) {
    val port: Int                                                         // L30 — actual port after start()
    override fun start()                                                  // L38
    override fun stop()                                                   // L49
    fun isRunning(): Boolean                                              // L55
    suspend fun processSegmentUrl(url: String, headers: Map<String, String> = emptyMap()): ByteArray   // L175
    fun createLocalUrl(m3u8Url: String): String                           // L275 — "http://localhost:$port/m3u8?url=<encoded>"
}
```
Routes: `GET /m3u8?url=…` (fetch + rewrite segment URLs to local `/segment?url=…`), `GET /segment?url=…` (fetch + strip fake JPEG/PNG/GIF header via `AutoDetector`, stream as `video/mp2t`), `GET /health`.

### 3b. `M3u8ServerManager` — high-level lifecycle wrapper (`M3u8ServerManager.kt`, 86 lines) ★ USE THIS
```kotlin
class M3u8ServerManager(private val client: OkHttpClient) {
    @Synchronized fun startServer(port: Int = 0)                          // L20 — idempotent
    @Synchronized fun stopServer()                                       // L41
    fun isRunning(): Boolean                                             // L50
    fun getServerUrl(): String?                                          // L55 — "http://localhost:<port>"
    fun processM3u8Url(m3u8Url: String): String?                         // L62 — returns local /m3u8?url=… URL
    suspend fun processSegmentUrl(segmentUrl: String, headers: Map<String, String> = emptyMap()): ByteArray?   // L70
    fun getServerInfo(): String                                          // L75
}
```

### 3c. `M3u8Integration` — `Video`-list convenience wrapper (`M3u8Integration.kt`, 93 lines)
```kotlin
class M3u8Integration(client: OkHttpClient, serverManager: M3u8ServerManager = M3u8ServerManager(client)) {
    fun processVideoList(videos: List<Video>): List<Video>               // L52
    fun getServerInfo(): String                                          // L78
    fun stopServer()                                                     // L83
    fun isServerRunning(): Boolean                                       // L91
}
```
> ⚠️ **`M3u8Integration` is NOT used by any real consumer** (Animetsu uses `M3u8ServerManager` directly with its own retry/restart loop). Treat `M3u8ServerManager` as the de-facto API.

### 3d. `AutoDetector` — fake-header skipper (`AutoDetector.kt`, 164 lines)
`object AutoDetector { fun detectSkipBytes(data: ByteArray): Int }` — returns 0 for valid MPEG-TS/MP4/AVI; for JPEG/PNG/GIF-disguised streams, scans first 4 KB for `ftyp` atom / `RIFF` / MPEG-TS sync byte `0x47` at 188-byte strides.

### When to use vs `playlistutils` direct
- **`playlistutils` direct:** m3u8 has plain `#EXT-X-STREAM-INF:` variants + normal MPEG-TS segments. Player (mpv) fetches segments itself with `Video.headers`.
- **`m3u8server`:** (a) segments disguised with JPEG/PNG/GIF magic bytes (anti-scrape CDNs), (b) player can't be told per-segment headers, (c) site rotates segment URLs and you want one stable local URL.

### Usage example (real, `src/all/animetsu/.../Animetsu.kt:518-577`)
```kotlin
private val m3u8ServerManager by lazy { M3u8ServerManager(m3u8Client) }

private suspend fun processHls(fullUrl, videoNameGen, watchReferer, subtitleTracks): List<Video> {
    val videos = playlistUtils.extractFromHls(playlistUrl = fullUrl, referer = watchReferer, masterHeaders = vidHeaders, videoHeaders = vidHeaders, videoNameGen = videoNameGen, subtitleList = subtitleTracks)
    return videos.mapNotNull { video ->
        val processedUrl = getProcessedM3u8Url(video.url) ?: return@mapNotNull null
        Video(url = processedUrl, quality = video.quality, videoUrl = processedUrl, headers = video.headers, subtitleTracks = video.subtitleTracks, audioTracks = video.audioTracks)
    }
}

private suspend fun getProcessedM3u8Url(originalUrl: String): String? {
    repeat(3) { attempt ->
        try {
            if (!m3u8ServerManager.isRunning()) { m3u8ServerManager.startServer(); delay(200L) }
            val processedUrl = m3u8ServerManager.processM3u8Url(originalUrl)
            if (processedUrl != null) return processedUrl
            m3u8ServerManager.stopServer(); delay(500L)
        } catch (e: Exception) { m3u8ServerManager.stopServer(); delay(500L) }
    }
    return null
}
```

**Notes:** server never auto-stops; extensions must call `stopServer()` (none do — keep running for app lifetime, fine since localhost-only random-port). `runBlocking` in handlers blocks NanoHTTPD worker threads (fine for low concurrency). Only 2 yuzono consumers — niche tool.

---

## 4. `universalextractor/` — WebView-based fallback extractor (120 lines)

**Files:** `lib/universalextractor/build.gradle.kts` (dep: `:lib:playlistutils`) + `src/aniyomi/lib/universalextractor/UniversalExtractor.kt` (120 lines)

**Main class (L22):**
```kotlin
class UniversalExtractor(private val client: OkHttpClient) {
    private val context: Application by injectLazy()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    fun videosFromUrl(origRequestUrl: String, origRequestHeader: Headers, customQuality: String? = null, prefix: String = ""): List<Video>
    companion object { const val TIMEOUT_SEC: Long = 10; private val VIDEO_REGEX by lazy { Regex(".*\\.(mp4|m3u8|mpd)(\\?.*)?$") } }
}
```

### How it works (L27-105)
1. Derive host label from `origRequestUrl.toHttpUrl().host.substringBefore(".").proper()` (title-cased).
2. Lazy-init `PlaylistUtils(client, origRequestHeader)`.
3. `CountDownLatch(1)` + `Handler(Looper.getMainLooper()).post { … }` to create a `WebView` on the main thread.
4. Configure WebView: `javaScriptEnabled=true`, `domStorageEnabled=true`, `databaseEnabled=true`, `userAgentString = origRequestHeader["User-Agent"]`.
5. `WebViewClient.shouldInterceptRequest` checks `VIDEO_REGEX.containsMatchIn(url)`; on match stores `resultUrl` + `latch.countDown()`.
6. `webView.loadUrl(origRequestUrl, headers)`.
7. `latch.await(TIMEOUT_SEC, SECONDS)` — 10s timeout.
8. Cleanup: `handler.post { webView.stopLoading(); webView.destroy(); webView = null }`.
9. Terabox special case (L70-88): if `resultUrl` contains `"M3U8_AUTO_360"`, fan out to `["1080","720","480","360"]`, `playlistUtils.extractFromHls` each.
10. Dispatch on URL substring (L90-104): `"m3u8"`→`extractFromHls`, `"mpd"`→`extractFromDash`, `"mp4"`→single `Video`, else→`emptyList()`.

**Auto-detects:** the **media file type** (mp4/m3u8/mpd) by URL extension after the page's JS resolves the stream URL. Works on any site whose player JS ultimately loads a `*.mp4|*.m3u8|*.mpd` URL. ~69 yuzono consumers.

**When to use as fallback:** no dedicated host extractor, JS-driven embed, can't reverse-engineer the API. **Cost:** spins up a real Android WebView (10s timeout, main-thread creation) — slow, won't work in unit tests. Always try a dedicated extractor first.

**Usage pattern:**
```kotlin
private val universalExtractor by lazy { UniversalExtractor(client) }
// in videoListParse:
emptyList<Video>() + runCatching {
    universalExtractor.videosFromUrl(iframeUrl, headers, prefix = "Mirror")
}.getOrDefault(emptyList())
```

**Notes:** synchronous (`latch.await`) — NOT main-thread-safe. Captures the FIRST URL matching `VIDEO_REGEX` (if page preloads a low-quality trailer first, you get the wrong URL).

---

## 5. Three representative host-specific extractors

### 5a. `filemoonextractor/` — Filemoon (API + AES-GCM, 186 lines)
**Class (L21):** `class FilemoonExtractor(private val client: OkHttpClient) { fun videosFromUrl(url: String, prefix: String = "Filemoon - ", headers: Headers? = null): List<Video> }`
- Deps: `:lib:unpacker` (declared but unused — leftover), `:lib:playlistutils`.
- Flow: parse `mediaId` from URL → GET `/api/videos/$mediaId/embed/details` → GET `/api/videos/$mediaId/embed/playback` with `X-Embed-*` headers → if `sources` empty, `decrypt(playback)` via AES/GCM/NoPadding (base64url key parts + iv) → `parseAs<PlaybackResponse>()` → for each source `playlistUtils.extractFromHls(streamUrl, …)`.
- Real usage (`src/de/aniworld/.../AniWorld.kt:201`): `filemoonExtractor.videosFromUrl(url, "($language) $NAME_FILEMOON ", headers)`

### 5b. `streamtapeextractor/` — StreamTape (JS string-slicing, 34 lines)
**Class (L9):** `class StreamTapeExtractor(private val client: OkHttpClient) { fun videoFromUrl(url: String, quality: String = "Streamtape", subtitleList: List<Track> = emptyList()): Video? }`
- No extra deps.
- Flow: normalize URL to `https://streamtape.com/e/<id>` → `asJsoup()` → find `<script>` with `document.getElementById('robotlink')` → string-slice the JS concatenation (`robotlink.innerHTML = '/xyz' + ('xcd' + '1234')` → `https:/xyz1234`) → `Video(videoUrl, quality, videoUrl, subtitleTracks)`.
- Returns single `Video?` (no quality metadata; `quality` param is just a label).
- Real usage (`AniWorld.kt:199`): `streamTapeExtractor.videoFromUrl(url, "($language) $NAME_STAPE")?.let(::listOf)`

### 5c. `vidmolyextractor/` — VidMoly (m3u8 + parallel-catch, 59 lines)
**Class (L13):** `class VidMolyExtractor(private val client: OkHttpClient, headers: Headers = commonEmptyHeaders) { suspend fun videosFromUrl(iframeUrl: String, prefix: String = ""): List<Video> }`
- Deps: `:lib:playlistutils`, `:core`.
- Rewrites iframe URL host to `vidmoly.biz`, sets `Origin`/`Referer` headers.
- Flow: GET iframe → find `<script>` with `"sources"` → regex-extract `file:"..."` URLs → `parallelCatchingFlatMap { playlistUtils.extractFromHls(videoUrl, videoNameGen = { "VidMoly - $quality" }, masterHeaders = headers, videoHeaders = headers) }`.
- Real usage (`AniWorld.kt:202`): `vidmolyExtractor.videosFromUrl(url, "($language)")`

---

## 6. Cross-cutting: how `videoListParse(response, hoster)` orchestrates these

### Pattern A — Multi-hoster dispatch (`src/de/aniworld/.../AniWorld.kt:174-206`)
```kotlin
private val voeExtractor        by lazy { VoeExtractor(client, headers) }
private val doodExtractor       by lazy { DoodExtractor(client) }
private val streamTapeExtractor by lazy { StreamTapeExtractor(client) }
private val vidozaExtractor     by lazy { VidozaExtractor(client) }
private val filemoonExtractor   by lazy { FilemoonExtractor(client) }
private val vidmolyExtractor    by lazy { VidMolyExtractor(client, headers) }

override fun videoListParse(response: Response): List<Video> {
    val document = response.useAsJsoup()
    val redirectlink = document.select("ul.row li")
    val allowedHosters = PREF_HOSTER_NAMES - excludedHosters
    return redirectlink.parallelCatchingFlatMapBlocking { elm ->
        val language = getLanguage(elm.attr("data-lang-key"))
        val redirectgs = elm.selectFirst("a.watchEpisode")!!.attr("abs:href")
        val hoster = elm.select("a h4").text()
        val matchedHoster = allowedHosters.firstOrNull { hoster.contains(it, true) } ?: return@... emptyList()
        val url = getRedirectedUrl(redirectgs)
        when (matchedHoster) {
            NAME_VOE      -> voeExtractor.videosFromUrl(url, "($language) ")
            NAME_DOOD     -> doodExtractor.videoFromUrl(url, "($language)", false)?.let(::listOf)
            NAME_STAPE    -> streamTapeExtractor.videoFromUrl(url, "($language) $NAME_STAPE")?.let(::listOf)
            NAME_VIZ      -> vidozaExtractor.videoFromUrl(url, "($language) $NAME_VIZ")?.let(::listOf)
            NAME_FILEMOON -> filemoonExtractor.videosFromUrl(url, "($language) $NAME_FILEMOON ", headers)
            NAME_VIDMOLY  -> vidmolyExtractor.videosFromUrl(url, "($language)")
            else -> null
        } ?: emptyList()
    }
}
```
Key points: (1) extractors lazy + shared; (2) `parallelCatchingFlatMapBlocking` runs all hosters concurrently with per-hoster exception swallowing; (3) `allowedHosters = PREF_HOSTER_NAMES - excludedHosters` filters by user prefs; (4) `sort()` ranks by hoster pref then language.

### Pattern B — HLS + local proxy (`Animetsu`, §3 above)
`playlistutils` for variant extraction → `m3u8server` for per-segment header injection / fake-header stripping. Proxy URL replaces both `Video.url` and `Video.videoUrl`.

### Pattern C — Crypto + HLS (`KickAssAnimeExtractor`, §2 above)
`CryptoAES.decrypt(ct, key, iv) → JSON.parse → PlaylistUtils.extractFromHls`.

### Pattern D — Decrypt fallback chain (`SoloLatino`, §2 above)
`http` prefix → `CryptoAES.decryptCbcIV` → `CryptoAES.decrypt` → custom `decodeJwtLink` → null.

---

## 7. "Which extractor for which host" quick reference

| Host pattern | Recommended lib | Returns | Method |
|---|---|---|---|
| `filemoon.*` (api + AES-GCM) | `:lib:filemoonextractor` | `List<Video>` (HLS) | `FilemoonExtractor(client).videosFromUrl(url, prefix, headers)` |
| `streamtape.com/e/` | `:lib:streamtapeextractor` | `Video?` (direct mp4) | `StreamTapeExtractor(client).videoFromUrl(url, quality, subs)` |
| `vidmoly.biz` (or mirror) | `:lib:vidmolyextractor` | `List<Video>` (suspend) | `VidMolyExtractor(client, headers).videosFromUrl(iframeUrl, prefix)` |
| `doodstream`/`dood.so` | `:lib:doodextractor` | `Video?` | `DoodExtractor(client).videoFromUrl(url, quality, isDood)` |
| `voe.sx`/`voe-unblock` | `:lib:voeextractor` | `List<Video>` | `VoeExtractor(client, headers).videosFromUrl(url, prefix)` |
| `mixdrop.*` | `:lib:mixdropextractor` | `List<Video>` | `MixDropExtractor(client).videosFromUrl(url, prefix)` |
| `megacloud`/`rapidcloud` | `:lib:megacloudextractor`/`:lib:rapidcloudextractor` | `List<Video>` | (separate libs) |
| Any HLS `.m3u8` (resolved) | `:lib:playlistutils` | `List<Video>` | `PlaylistUtils(client, headers).extractFromHls(url, referer, masterHeaders, videoHeaders)` |
| Any DASH `.mpd` | `:lib:playlistutils` | `List<Video>` | `PlaylistUtils(client).extractFromDash(mpdUrl, videoNameGen, mpdHeaders, videoHeaders)` |
| HLS with disguised segments / header injection | `:lib:m3u8server` | (rewrites existing `List<Video>`) | `M3u8ServerManager(client).startServer(); … .processM3u8Url(url)` |
| Unknown JS-driven embed | `:lib:universalextractor` | `List<Video>` (WebView fallback, 10s) | `UniversalExtractor(client).videosFromUrl(url, headers, customQuality, prefix)` |
| Encrypted config/token (CryptoJS/OpenSSL/CBC-IV) | `:lib:cryptoaes` | `String` | `CryptoAES.decrypt(ct, password)` / `decrypt(ct, key, iv)` / `decryptCbcIV(ct, key)` |
| JSFuck numeric password | `:lib:cryptoaes` `Deobfuscator` | `String` | `Deobfuscator.deobfuscateJsPassword(input)` |

---

## 8. ★ ext-lib 16 migration: the legacy `Video` constructor problem (VERIFIED)

**The issue:** every `lib/` extractor that constructs `Video` uses the ext-lib 14 positional constructor:
```kotlin
Video(url, quality, videoUrl, headers = …, subtitleTracks = …, audioTracks = …)
//  ^pageUrl  ^label  ^streamUrl
```
In ext-lib v16 this constructor is `@Deprecated(level = ERROR)` — **it will not compile.** Verified locations:
- `playlistutils/PlaylistUtils.kt:102-109` (single-stream short-circuit)
- `playlistutils/PlaylistUtils.kt:192` (HLS variant)
- `playlistutils/PlaylistUtils.kt:358` (DASH variant)
- `universalextractor/UniversalExtractor.kt:101` (mp4 branch)
- `streamtapeextractor/StreamTapeExtractor.kt` (returns `Video(videoUrl, quality, videoUrl, subs)`)
- `m3u8server/M3u8Integration.kt` (rewrites Videos)
- Every other host extractor that returns `Video(...)`

**The fix (mechanical, pervasive):** rewrite each call to the v16 named-arg constructor:
```kotlin
// BEFORE (ext-lib 14, won't compile on v16):
Video(url, quality, videoUrl, headers = h, subtitleTracks = s, audioTracks = a)

// AFTER (ext-lib 16):
Video(videoUrl = videoUrl, videoTitle = quality, headers = h, subtitleTracks = s, audioTracks = a)
```
Note: the `url` (page URL) argument is **dropped** — v16 `Video` has no `url`/`videoPageUrl` field. The stream URL (`videoUrl`) and the label (`videoTitle`, formerly `quality`) are what matter.

**Migration scope:** when we copy any `lib/` module into our v16 extensions build, we must audit and rewrite every `Video(...)` construction. This is the single biggest mechanical change in porting yuzono libs to v16. A grep for `Video(` in each lib's `src/` finds them all.

**Also:** any `.copy(url = …)` or `.copy(quality = …)` calls won't compile (those fields don't exist on v16). Use `.copy(videoUrl = …, videoTitle = …)`.

---

## 9. Things I could NOT fully verify (honest notes)

1. **`FilemoonExtractor`'s `:lib:unpacker` dependency** is declared but never imported/used in source. Likely leftover. Didn't check git history.
2. **`M3u8Integration` zero consumers** — only `Animetsu` uses `m3u8server`, and it uses `M3u8ServerManager` directly. Treat `M3u8ServerManager` as the API.
3. **`AutoDetector.detectSkipBytes`** `MP4_FTYP` branch returns `ftypOffset - 4` — logic looks correct for ISO BMFF `ftyp` preceded by 4-byte size, but I didn't test against real disguised-MP4 streams.
4. **`cryptoaes` no PBKDF2/SHA-256/HKDF** — modern CryptoJS with PBKDF2 won't decrypt. Didn't enumerate which yuzono sites hit this.
5. **`UniversalExtractor` Terabox special case** (`M3U8_AUTO_360` fan-out) — hardcoded hack; didn't verify any extension hits it.
6. **`StreamTapeExtractor` returns single `Video`** with no quality metadata — actual quality unknown until playback.
7. **`VidMolyExtractor` mirror domains** — host rewrite always targets `vidmoly.biz`; extensions using mirrors may need to pre-normalize.
8. **`m3u8server` `processM3u8Url` returns null** only if `server == null` (never started). Animetsu's retry loop treats null as transient + restarts — defensive, not a documented contract.
9. **Dependency graphs of host extractors I didn't read** (`voeextractor`, `doodextractor`, `mixdropextractor`, `megacloudextractor`, `rapidcloudextractor`) — they may have their own `:lib:playlistutils`/`:lib:cryptoaes`/`:lib:unpacker` deps that propagate transitively.
10. **All these libs target ext-lib 14** (yuzono `versionName` prefix is `14.`). Signatures may need rechecking against v16 beyond just the `Video` ctor — I verified the `Video` ctor issue but did NOT exhaustively check every method signature against v16.

## 10. Related docs

- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` §6 — the v16 `Video` constructor (named args only).
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` §2 — the `Video` field/ctor diff (v16 vs runtime).
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md` — how the player consumes the `Video` objects these libs produce.
- `MEMORY/research/04-network-layer-and-interceptors.md` — the network layer these libs use (`client`, `awaitSuccess`, `asJsoup`).
- `MEMORY/research/05-keiyoushi-utils-core.md` — `keiyoushi.utils` (`parseAs`, `useAsJsoup`, `UrlUtils`, `parallelCatchingFlatMapBlocking`) these libs use.