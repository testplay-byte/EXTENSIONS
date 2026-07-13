# ext-lib 16 — Authoritative API Reference (Compile-Time)

> Last updated: 2026-06-22 · Status: VERIFIED against `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/` at tag `v16`
> (commit `782a5a6b`).
>
> **This documents the PUBLISHED stub library that extensions `compileOnly`-depend on** — i.e. what
> you can actually call/write in extension code. The app's runtime `source-api/` is a fuller superset;
> see `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` for the difference.

All file paths below are relative to `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/src/main/java/eu/kanade/tachiyomi/`.

---

## 1. Package map

```
eu.kanade.tachiyomi.animesource
├── AnimeSource.kt                      interface — the 6 core suspend methods
├── AnimeCatalogueSource.kt             interface — adds popular/search/latest + filters
├── AnimeSourceFactory.kt               abstract class — for multi-source extensions
├── ConfigurableAnimeSource.kt           interface — setupPreferenceScreen(screen)
├── UnmeteredSource.kt                   marker interface
├── model/
│   ├── SAnime.kt                        interface — anime metadata (url, title, status, fetch_type, …)
│   ├── SEpisode.kt                      interface — episode metadata (url, name, scanlator, …)
│   ├── Video.kt                         data class — Video + Track + TimeStamp + ChapterType
│   ├── Hoster.kt                        class — a hoster page that yields Videos
│   ├── FetchType.kt                     enum — Seasons / Episodes (ext-lib 16)
│   ├── AnimeFilter.kt / AnimeFilterList.kt
│   ├── AnimeUpdateStrategy.kt
│   └── AnimesPage.kt
└── online/
    ├── AnimeHttpSource.kt               abstract class — THE base class for HTTP sources
    ├── ParsedAnimeHttpSource.kt         @Deprecated abstract class — Jsoup-selector helpers
    └── ResolvableAnimeSource.kt         interface — deep-link URI resolution (ext-lib 14)
```

The lib also publishes stubs under `eu.kanade.tachiyomi.network.*` (`NetworkHelper`, `OkHttpExtensions`,
`Requests`, `JavaScriptEngine`, `RateLimitInterceptor`, `SpecificHostRateLimitInterceptor`) and
`eu.kanade.tachiyomi.util.JsoupExtensions` and `tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem`.
These are `Stub!` — the app provides real impls at runtime. Extensions call them as if real.

---

## 2. `AnimeSource` interface — `animesource/AnimeSource.kt`

The minimal interface. **6 members only** (no legacy `getVideoList(episode)`, no Rx `fetch*`):

```kotlin
interface AnimeSource {
    val id: Long
    val name: String

    suspend fun getAnimeDetails(anime: SAnime): SAnime                      // ext-lib 14
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>               // ext-lib 14
    suspend fun getSeasonList(anime: SAnime): List<SAnime>                  // ext-lib 16
    suspend fun getHosterList(episode: SEpisode): List<Hoster>              // ext-lib 16
    suspend fun getVideoList(hoster: Hoster): List<Video>                   // ext-lib 16
}
```

> NOTE: there is **no `getVideoList(episode: SEpisode)`** in the v16 interface. The only
> `getVideoList` takes a `Hoster`. The legacy episode-based video fetch is gone from the interface
> (it survives as `@Deprecated` only inside `AnimeHttpSource`).

---

## 3. `AnimeCatalogueSource` interface — `animesource/AnimeCatalogueSource.kt`

Extends `AnimeSource`. Adds browse/search:

```kotlin
interface AnimeCatalogueSource : AnimeSource {
    val lang: String
    val supportsLatest: Boolean

    suspend fun getPopularAnime(page: Int): AnimesPage
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage
    suspend fun getLatestUpdates(page: Int): AnimesPage
    fun getFilterList(): AnimeFilterList
}
```

---

## 4. `AnimeHttpSource` abstract class — `online/AnimeHttpSource.kt`  ★ THE base class

> **All method bodies throw `Exception("Stub!")` in the published lib.** The app provides real impls
> at runtime. Signatures below are exact (copied from v16 source).

### 4.1 Properties (stubs; override `baseUrl`/`client`/`headersBuilder()` as needed)

| Member | Signature | Notes |
|---|---|---|
| `network` | `protected val network: NetworkHelper` | access to the shared OkHttpClient + prefs. Stub. |
| `baseUrl` | `abstract val baseUrl: String` | **you MUST override.** No trailing slash. |
| `versionId` | `open val versionId: Int` | bump if URLs change to be treated as a new source. |
| `id` | `override val id: Long` | auto-generated from `name/lang/versionId` MD5. Don't override normally. |
| `headers` | `val headers: Headers` | result of `headersBuilder()`. |
| `client` | `open val client: OkHttpClient` | override to add interceptors (Cloudflare, rate-limit). Default = `network.client`. |
| `headersBuilder()` | `protected open fun headersBuilder(): Headers.Builder` | override to add Referer/User-Agent. |

### 4.2 Abstract methods you MUST implement (compile errors if not)

```kotlin
// Catalogue (browse/search)
protected abstract fun popularAnimeRequest(page: Int): Request
protected abstract fun popularAnimeParse(response: Response): AnimesPage
protected abstract fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request
protected abstract fun searchAnimeParse(response: Response): AnimesPage
protected abstract fun latestUpdatesRequest(page: Int): Request
protected abstract fun latestUpdatesParse(response: Response): AnimesPage

// Details + episodes
protected abstract fun animeDetailsParse(response: Response): SAnime
protected abstract fun episodeListParse(response: Response): List<SEpisode>

// ext-lib 16 — seasons (required even if you only use Episodes; throw UnsupportedOperationException if unused)
protected abstract fun seasonListParse(response: Response): List<SAnime>

// ext-lib 16 — hosters (required)
protected abstract fun hosterListParse(response: Response): List<Hoster>
```

> `latestUpdatesRequest/Parse` are abstract even if `supportsLatest = false`. The yuzono pattern is
> to throw `UnsupportedOperationException()` in them when latest isn't supported.

> `seasonListParse` is abstract. If your site has no seasons concept, the yuzono `core/Source.kt`
> pattern throws `UnsupportedOperationException`. Only set `SAnime.fetch_type = FetchType.Seasons`
> if you actually implement seasons.

### 4.3 ext-lib 16 video pipeline methods (the new flow)

```kotlin
// Stage 1: hosters for an episode
override suspend fun getHosterList(episode: SEpisode): List<Hoster>          // stub default
protected open fun hosterListRequest(episode: SEpisode): Request             // default GET(baseUrl + episode.url, headers)
protected abstract fun hosterListParse(response: Response): List<Hoster>     // YOU implement

// Stage 2: videos for a hoster
override suspend fun getVideoList(hoster: Hoster): List<Video>               // stub default
protected open fun videoListRequest(hoster: Hoster): Request                 // default GET(hoster.hosterUrl, headers)
protected open fun videoListParse(response: Response, hoster: Hoster): List<Video>   // open (stub) — override to parse

// Stage 3: lazy per-video resolution (override for protected/expiring URLs)
open suspend fun resolveVideo(video: Video): Video?                          // default: return video (no-op)… BUT in published v16 the body is Stub! — the app's runtime default is `return video`

// Sorting (override for user-preference ordering; called by the app, not by you)
open fun List<Hoster>.sortHosters(): List<Hoster>                            // default: return this (REAL default, not stub)
protected open fun List<Video>.sortVideos(): List<Video>                     // stub default — override to implement
```

**Important nuances:**
- `videoListParse(response, hoster)` is `open`, NOT `abstract` — but its body is `Stub!`, so you
  MUST override it to do anything useful.
- `resolveVideo`'s published body is `Stub!`; the **app's runtime default is `return video`**
  (no-op). So if you don't override it, videos play with their `videoUrl` as-is. Override it ONLY
  when the `videoUrl` needs a per-play fetch (e.g. expiring token, must visit a page first).
- `sortVideos` is `protected open` (stub). Override it to reorder by quality/language. **The KDoc
  example in the ext-lib source uses `it.quality.contains(...)` — but `quality` does NOT exist in
  v16 `Video`.** Use `it.videoTitle.contains(...)` instead. (This is a stale-doc bug in the ext-lib.)
- `sortHosters` is `open` with a real `return this` default (not stub). Override to put preferred
  hosters first.

### 4.4 ext-lib 16 seasons methods (orthogonal to video pipeline)

```kotlin
override suspend fun getSeasonList(anime: SAnime): List<SAnime>              // stub default
protected open fun seasonListRequest(anime: SAnime): Request                 // default GET(baseUrl + anime.url, headers)
protected abstract fun seasonListParse(response: Response): List<SAnime>     // YOU implement (or throw if unused)
```

Only invoked when `SAnime.fetch_type == FetchType.Seasons`. Each returned `SAnime` is itself a
"season" with `fetch_type = FetchType.Episodes` and a `season_number`, and goes through the normal
episode → hoster → video flow.

### 4.5 Legacy / deprecated methods (still present in v16, avoid for new code)

```kotlin
@Deprecated("Use resolveVideo instead") open suspend fun getVideoUrl(video: Video): String
@Deprecated("Use resolveVideo instead") protected open fun videoUrlRequest(video: Video): Request
@Deprecated("Use resolveVideo instead") protected open fun videoUrlParse(response: Response): String
```
These are the ext-lib 1.5 per-video resolver. **Do NOT use in new ext-lib 16 extensions** — use
`resolveVideo`. They exist only so very old extensions still load. (Note: the v16 published lib does
NOT declare a legacy `getVideoList(episode)` / `videoListParse(response)` / `videoListSelector` /
`videoFromElement` — those are gone from the interface entirely; they survive only in the app's
runtime source-api for backward compat with installed old extensions.)

### 4.6 URL helpers + misc

```kotlin
fun SEpisode.setUrlWithoutDomain(url: String)          // stub — stores path only (survives domain changes)
fun SAnime.setUrlWithoutDomain(url: String)            // stub
open fun getAnimeUrl(anime: SAnime): String            // ext-lib 14, stub — override to fix "open in webview"
open fun getEpisodeUrl(episode: SEpisode): String      // ext-lib 14, stub
open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) {}   // REAL empty default — override to tweak new episodes
protected fun generateId(name: String, lang: String, versionId: Int): Long   // ext-lib 14, stub
override fun getFilterList(): AnimeFilterList          // stub
```

---

## 5. `Hoster` class — `model/Hoster.kt`  (ext-lib 16)

```kotlin
class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
) {
    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"
        fun List<Video>.toHosterList(): List<Hoster> = listOf(Hoster(hosterUrl = "", hosterName = NO_HOSTER_LIST, videoList = this))
    }
}
```

| Field | Type | Default | Purpose |
|---|---|---|---|
| `hosterUrl` | `String` | `""` | URL the app GETs in `videoListRequest(hoster)` (default impl does `GET(hoster.hosterUrl, headers)`). |
| `hosterName` | `String` | `""` | Display name in the quality sheet header. |
| `videoList` | `List<Video>?` | `null` | If non-null, the app uses these directly and **skips** `getVideoList(hoster)`. If null, app calls `getVideoList(hoster)`. |
| `internalData` | `String` | `""` | Opaque string round-tripped through serialization. Use for any data you need in `videoListParse(response, hoster)` or `resolveVideo`. |
| `lazy` | `Boolean` | `false` | If true, app shows "Tap to load" and defers `getVideoList(hoster)` until the user expands the hoster. |

**Constraints in v16 (compile-time):**
- It's a plain `class`, NOT `data class`, NOT `open`. **You cannot subclass it, and there is no
  `copy()` method.** To "modify" a Hoster, construct a new one.
- There is **no `status` field** in the published v16 (the app's runtime version has one, but you
  can't touch it from extension code).
- `NO_HOSTER_LIST` sentinel: if you return a flat `List<Video>` from a legacy `getVideoList(episode)`
  flow, the app wraps it via `toHosterList()` into a single Hoster with `hosterName = NO_HOSTER_LIST`
  and the quality sheet renders it as a flat list. For new ext-lib 16 extensions, **don't use this** —
  return real hosters.

**Eager vs lazy hoster — when to use which:**
- **Eager** (`lazy = false`, `videoList = null`): app fetches videos for all hosters in parallel as
  soon as the episode opens. Use when fetching is cheap.
- **Eager with pre-filled videos** (`videoList = <list>`): app uses them directly, no extra request.
  Use when you already have the video URLs at hoster-list time.
- **Lazy** (`lazy = true`, `videoList = null`): app shows hoster names only, fetches on expand. Use
  when each hoster page is expensive or rate-limited, to avoid hammering all of them upfront.

---

## 6. `Video` data class — `model/Video.kt`  (ext-lib 16)

```kotlin
@Suppress("unused_parameter")
data class Video(
    val videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
)
```

The legacy constructor `Video(url, quality, videoUrl, headers?, …)` is `@Deprecated(level = ERROR)` —
**it will not compile in v16.** Use the new constructor with named args.

| Field | Type | Default | Read by player? | Purpose |
|---|---|---|---|---|
| `videoUrl` | `String` (val) | `""` | **YES** — passed to mpv `loadfile`. Empty/`"null"` ⇒ treated as unresolved. | Direct stream URL (m3u8, mp4, …). |
| `videoTitle` | `String` | `""` | **YES** — quality-sheet label. | Human-readable label ("1080p", "Sub - Vidmoly", …). |
| `resolution` | `Int?` | `null` | no (future sorting) | Vertical resolution (1080, 720…). |
| `bitrate` | `Int?` | `null` | no | Bitrate. |
| `headers` | `okhttp3.Headers?` | `null` | **YES** — pushed to mpv `http-header-fields`. Falls back to `source.headers`. | Per-video HTTP headers (Referer, Origin, cookies…). |
| `preferred` | `Boolean` | `false` | **YES** — auto-selected first. | Set true on the best quality / user-preferred. |
| `subtitleTracks` | `List<Track>` | `[]` | **YES** — mpv `sub-add <url> auto <lang>`. | External subs. |
| `audioTracks` | `List<Track>` | `[]` | **YES** — mpv `audio-add <url> auto <lang>`. | External audio (dub). |
| `timestamps` | `List<TimeStamp>` | `[]` | **YES** — merged into mpv chapters. | OP/ED/recap markers. |
| `mpvArgs` | `List<Pair<String,String>>` | `[]` | **YES** — joined `opt="val",…` into mpv `loadfile` 5th arg. | Per-video mpv options. |
| `ffmpegStreamArgs` | `List<Pair<String,String>>` | `[]` | no (downloads only) | FFmpeg stream args for download. |
| `ffmpegVideoArgs` | `List<Pair<String,String>>` | `[]` | no (downloads only) | FFmpeg video args for download. |
| `internalData` | `String` | `""` | no (round-tripped via serialization) | Opaque data for `resolveVideo`/`videoListParse`. |
| `initialized` | `Boolean` | `false` | **YES** — if false, app calls `source.resolveVideo(video)` before playing. | Guards against re-resolving. |

**Constraints in v16 (compile-time):**
- **No `status` field** (app runtime has one, but you can't set it).
- **No `url` / `quality` getters** (gone; use `videoUrl` / `videoTitle`).
- **No `videoPageUrl`** (app runtime has it for legacy compat; not in v16).
- It IS a `data class`, so `copy()` works. Use `video.copy(videoUrl = resolved, initialized = true)`
  from `resolveVideo`.

### Supporting types (same file)

```kotlin
data class Track(val url: String, val lang: String)                 // for subtitleTracks & audioTracks
enum class ChapterType { Opening, Ending, Recap, MixedOp, Other }
data class TimeStamp(val start: Double, val end: Double, val name: String, val type: ChapterType = ChapterType.Other)
```
`start`/`end` are **seconds** (Double).

---

## 7. `FetchType` enum — `model/FetchType.kt`  (ext-lib 16)

```kotlin
enum class FetchType { Seasons, Episodes }
```
Stored on `SAnime.fetch_type`. Default `Episodes`. Set to `Seasons` only if you implement
`seasonListParse` and the site organizes content as seasons-of-anime. Once initialized, the app
does not change it.

---

## 8. `SAnime` interface — `model/SAnime.kt`

```kotlin
interface SAnime {
    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?                  // comma-separated
    var status: Int                      // see companion constants
    var thumbnail_url: String?
    var background_url: String?
    var update_strategy: AnimeUpdateStrategy
    var fetch_type: FetchType            // ext-lib 16
    var season_number: Double            // ext-lib 16
    var initialized: Boolean             // tells app if getAnimeDetails should be called
    companion object {
        const val UNKNOWN = 0; ONGOING = 1; COMPLETED = 2; LICENSED = 3
        const val PUBLISHING_FINISHED = 4; CANCELLED = 5; ON_HIATUS = 6
        fun create(): SAnime             // stub — app provides real impl
    }
}
```
Create instances with `SAnime.create()`. Fill `url`, `title`, `status`, `thumbnail_url`,
`update_strategy = AnimeUpdateStrategy.ALWAYS_UPDATE` (default), `fetch_type = FetchType.Episodes`
(default). `url` should be path-only (use `setUrlWithoutDomain`) so it survives domain changes.

---

## 9. `SEpisode` interface — `model/SEpisode.kt`

```kotlin
interface SEpisode {
    var url: String
    var name: String
    var date_upload: Long                // epoch millis
    var episode_number: Float
    var fillermark: Boolean
    var scanlator: String?               // ★ THIS is where sub/dub availability goes (project rule §8)
    var summary: String?
    var preview_url: String?
    companion object { fun create(): SEpisode }   // stub
}
```

> **Project rule §8 (Episode Display):** sub/dub availability must be shown via the `scanlator` field
> (shows below the episode name in the UI), NOT crammed into `name`. Episode `name` stays clean
> (number + title only). This is verified: `scanlator` exists in v16 `SEpisode` and the Aniyomi UI
> renders it below the episode name.

---

## 10. `ParsedAnimeHttpSource` — `online/ParsedAnimeHttpSource.kt`  (⚠️ @Deprecated)

```kotlin
@Deprecated("In most cases sources only require a subset of the methods from this class. " +
            "Source developers should make their own implementation according to their needs.")
abstract class ParsedAnimeHttpSource : AnimeHttpSource()
```

Provides **Jsoup selector-based stubs** for: popular/search/latest (Selector + FromElement +
NextPageSelector), `animeDetailsParse(document)`, `episodeListSelector`/`episodeFromElement`,
`seasonListSelector`/`seasonFromElement`.

**CRITICAL:** the v16 `ParsedAnimeHttpSource` does **NOT** provide hoster/video selector hooks
(no `hosterListSelector`, no `videoListSelector`, no `videoFromElement`). The app's runtime
`source-api` version DOES have them, but you compile against the published lib which does NOT.
So if you extend `ParsedAnimeHttpSource`, you still implement `hosterListParse` and
`videoListParse(response, hoster)` yourself.

**Recommendation for new ext-lib 16 extensions:** extend `AnimeHttpSource` directly (not
`ParsedAnimeHttpSource`). Implement parse methods with Jsoup yourself. This avoids the deprecation
warning and gives full control. The yuzono extensions extend `ParsedAnimeHttpSource` because they're
on ext-lib 14 where it wasn't deprecated.

---

## 11. `ResolvableAnimeSource` — `online/ResolvableAnimeSource.kt`  (ext-lib 14)

```kotlin
interface ResolvableAnimeSource : AnimeSource {
    fun getUriType(uri: String): UriType              // Anime | Episode | Unknown
    suspend fun getAnime(uri: String): SAnime?        // called if getUriType == Anime
    suspend fun getEpisode(uri: String): SEpisode?    // called if getUriType == Episode
}
sealed interface UriType { data object Anime; data object Episode; data object Unknown }
```
Implement this + ship a `*UrlActivity` (deep-link intent filter) so users can "Share → Aniyomi" a
URL and have the app open the right anime/episode. See yuzono `src/en/miruro/AndroidManifest.xml`
+ `MiruroUrlActivity.kt` for the pattern.

---

## 12. `ConfigurableAnimeSource` — `animesource/ConfigurableAnimeSource.kt`

```kotlin
interface ConfigurableAnimeSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
```
Implement this on your source to expose user preferences (quality, server preference, dub/sub, …).
The app calls it to build the source's settings screen. Use the preference helpers from
`core/` (`keiyoushi.utils.getPreferencesLazy`, `PreferenceScreen.get/addListPreference`, etc.).

---

## 13. `AnimeSourceFactory` — `animesource/AnimeSourceFactory.kt`

For extensions that ship multiple sources in one APK:
```kotlin
abstract class AnimeSourceFactory {
    abstract fun createSources(): List<AnimeSource>
}
```
The `extClass` in `AndroidManifest.xml` then points to your factory class instead of a single source.
Each source gets its own `id` derived from its `name`/`lang`/`versionId`.

---

## 14. Quick "what to implement" checklist for a basic ext-lib 16 HTTP source

Extend `AnimeHttpSource`, implement `ConfigurableAnimeSource` if you want preferences:

- [ ] `name`, `baseUrl`, `lang`, `supportsLatest` (override `supportsLatest = false` if no latest)
- [ ] `popularAnimeRequest` / `popularAnimeParse`
- [ ] `searchAnimeRequest` / `searchAnimeParse` (+ `getFilterList` for filters)
- [ ] `latestUpdatesRequest` / `latestUpdatesParse` (throw `UnsupportedOperationException` if unused)
- [ ] `animeDetailsParse`
- [ ] `episodeListParse`
- [ ] `seasonListParse` (throw `UnsupportedOperationException` if no seasons concept)
- [ ] **`hosterListParse`** (ext-lib 16 — the server list)
- [ ] **`videoListParse(response, hoster)`** (ext-lib 16 — videos per hoster)
- [ ] `resolveVideo(video)` (only if URLs need per-play resolution; otherwise leave default)
- [ ] `List<Hoster>.sortHosters()` (optional — user preference)
- [ ] `List<Video>.sortVideos()` (optional — quality/language preference)
- [ ] `setupPreferenceScreen(screen)` (if `ConfigurableAnimeSource`)
- [ ] (optional) `ResolvableAnimeSource` + `*UrlActivity` for deep links

See `MEMORY/research/02-reference-extension-build-and-structure.md` for the file layout, manifest,
and build.gradle that accompany this.
