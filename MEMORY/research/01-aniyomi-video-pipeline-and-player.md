# Aniyomi Video Pipeline — How the App Consumes Video/Hoster

> Last updated: 2026-06-22 · Status: VERIFIED
> Source: `SHARED/REFERENCE_HUB/aniyomi-app/` (app runtime `source-api/` + `app/src/main/` player code).
>
> This documents the **RUNTIME** behavior — what the Aniyomi app actually does with the
> `Video`/`Hoster` objects your extension produces. For the compile-time API you write against, see
> `MEMORY/ext-lib/02-ext-lib-16-api-reference.md`. For the difference between the two, see
> `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md`.

---

## 1. The player is MPV-based, NOT ExoPlayer

A codebase-wide search for `ExoPlayer|HlsMediaSource|ExtractorMediaSource|DefaultHttpDataSource|
androidx.media3|MediaItem` across `SHARED/REFERENCE_HUB/aniyomi-app/app/src/main` returned **zero hits**.
Aniyomi uses **libmpv** (`is.xyz.mpv.MPVLib`) throughout `PlayerActivity.kt`.

So the "media source wiring" is just mpv `loadfile` + `setOptionString("http-header-fields", …)`,
not ExoPlayer `MediaItem` builders. This is why `Video` has `mpvArgs` and not exo-args.

---

## 2. The three-stage ext-lib 16 video pipeline

```
Stage 1: getHosterList(episode)  ──►  List<Hoster>     (one Hoster per server/embed page)
   │   app calls source.run { it.sortHosters() } immediately after
   │
Stage 2: getVideoList(hoster)    ──►  List<Video>      (videos on one hoster page)
   │   app calls source.run { it.sortVideos() } after
   │   (skipped if hoster.videoList != null — eager pre-filled)
   │
Stage 3: resolveVideo(video)     ──►  Video?            (per-video lazy URL resolution at play time)
       app calls only if source is AnimeHttpSource && !video.initialized
       result is .copy(initialized = true); null/empty ⇒ mark ERROR, try next best
```

### How the app decides new-flow vs legacy-flow

`EpisodeLoader.checkHasHosters(source)` (`app/.../data/download/anime/EpisodeLoader.kt:60-78`)
walks the source's class hierarchy (stopping at `ParsedAnimeHttpSource` / `AnimeHttpSource` /
`AnimeSource`) and checks whether any declared method is named `getHosterList`,
`hosterListRequest`, or `hosterListParse`.

- **If yes** (our ext-lib 16 extensions — we implement `hosterListParse`): **new flow** —
  `getHosterList(episode)` → `sortHosters()`.
- **If no** (legacy ext-lib ≤14 extensions): **legacy flow** — `getVideoList(episode)` →
  `sortVideos()` → `.toHosterList()` (wrapped into a single `NO_HOSTER_LIST` hoster).

> ⚠️ This detection is by **method name**, not by ext-lib version. So an extension compiled against
> v16 that still only implements the legacy `getVideoList(episode)`/`videoListParse(response)` would
> be detected as legacy — BUT those methods don't exist in the v16 published interface, so you
> couldn't compile such an extension against v16 anyway. On v16, you MUST use the new flow.

### The legacy `videoUrl == "null"` bridge (mostly irrelevant for v16 new extensions)

`EpisodeLoader.parseVideoUrls(source)` (`EpisodeLoader.kt:180`) checks each video: if
`video.videoUrl == "null"` (literal string), it calls the deprecated `source.getVideoUrl(video)` to
resolve. This exists for very old extensions. **New ext-lib 16 extensions set a real `videoUrl` (or
empty `""` + rely on `resolveVideo`), so this bridge never fires.** Note: empty `""` is different
from `"null"` — `""` skips the legacy bridge and the video fails at play time unless `resolveVideo`
produces a non-empty URL.

---

## 3. Full resolution flow (extension → app → mpv)

```
[Extension]                                  [Aniyomi app]                           [mpv player]

getPopularAnime / getSearchAnime             AnimeScreenModel.fetchEpisodesFromSource
  └─ getAnimeDetails(anime)                   └─ getEpisodeList(anime) → episodes in DB

User taps episode ─► PlayerActivity ─► PlayerViewModel.loadEpisode(episodeId)
                                          │
                                          └─ EpisodeLoader.getHosters(episode, anime, source)
                                             ├─ downloaded?  → buildVideo(localFile) → toHosterList()
                                             ├─ LocalSource?→ Video(localUri, "Local source: …") → toHosterList()
                                             └─ AnimeHttpSource?
                                                 ├─ checkHasHosters(source)? ──► NEW flow:
                                                 │     source.getHosterList(episode) → List<Hoster>
                                                 │     .let { source.run { it.sortHosters() } }
                                                 └─ else ──► LEGACY flow:
                                                       source.getVideoList(episode) → List<Video>
                                                       .let { source.run { it.sortVideos() } }
                                                       .toHosterList()

PlayerViewModel.loadHosters(source, hosterList, hosterIdx, videoIdx)
  └─ per Hoster in parallel: EpisodeLoader.loadHosterVideos(source, hoster)
       └─ getVideos(source, hoster)
            ├─ hoster.videoList != null? use directly (skip network)
            ├─ else: source.getVideoList(hoster)          ← Stage 2
            └─ parseVideoUrls(source): for video where videoUrl=="null": source.getVideoUrl(video)  [legacy bridge]
            └─ source.run { videos.sortVideos() }
       → HosterState.Ready(hosterName, videoList, all-QUEUE)

  Auto-pick: first video.preferred across ready hosters, else HosterLoader.selectBestVideo
  └─ PlayerViewModel.loadVideo(source, video, hosterIdx, videoIdx)
       └─ if videoState != READY:
            HosterLoader.getResolvedVideo(source, video)
              └─ if source is AnimeHttpSource && !video.initialized:
                   source.resolveVideo(video)              ← Stage 3 (override for lazy URL)
              └─ .copy(initialized = true)
       └─ resolvedVideo == null || videoUrl empty? → mark ERROR, retry selectBestVideo
       └─ else: _currentVideo.value = resolvedVideo; activity.setVideo(resolvedVideo)

PlayerActivity.setVideo(video):
  ├─ setHttpOptions(video):  MPVLib.setOptionString("http-header-fields",
  │     (video.headers ?: source.headers).toMultimap().mapValues{it.value.first()}.entries
  │       .joinToString(",") { "${k}: ${v.replace(",", "\\,")}" })
  ├─ start-position handling (episode.last_second_seen)
  └─ MPVLib.command(["loadfile", parseVideoUrl(video.videoUrl), "replace", "0",
                     video.mpvArgs.joinToString(",") { "$opt=\"$val\"" }])

mpv fileLoaded event:
  ├─ setMpvOptions(): for downloaded videos, re-apply mpvArgs from mpv metadata[Video.MPV_ARGS_TAG]
  ├─ setupTracks():
  │     audioTracks.forEach    { mpv "audio-add <url> auto <lang>" }
  │     subtitleTracks.forEach { mpv "sub-add <url> auto <lang>" }
  └─ setupChapters(): ChapterUtils.mergeChapters(currentChapters, video.timestamps, duration)

External player path (ExternalIntents.getExternalIntent):
  ├─ HosterLoader.getBestVideo(source, hosters) → Video
  ├─ HosterLoader.getResolvedVideo(source, video) → resolves via source.resolveVideo if needed
  └─ Intent: videoUrl, headers, subs (+subs.name/enable/subtitles_location), picked subtitle
```

---

## 4. Which `Video` fields the player ACTUALLY reads

| Field | Read? | Where / how |
|---|---|---|
| `videoUrl` | ✅ YES | `PlayerActivity.setVideo` → mpv `loadfile`. Empty ⇒ fail. |
| `videoTitle` | ✅ YES | Quality sheet label (`QualitySheet.VideoText`). |
| `headers` | ✅ YES | `PlayerActivity.setHttpOptions` → mpv `http-header-fields`. Falls back to `source.headers`. Also used by `getVideoSize` (downloads) + external intents. |
| `preferred` | ✅ YES | `HosterLoader.selectBestVideo` + `PlayerViewModel.loadHosters` auto-select priority. |
| `subtitleTracks` | ✅ YES | `PlayerActivity.setupTracks` → mpv `sub-add`. External intents: `subs`/`subs.name`/`subs.enable`/`subtitles_location`. |
| `audioTracks` | ✅ YES | `PlayerActivity.setupTracks` → mpv `audio-add`. (External intents don't use audio.) |
| `timestamps` | ✅ YES | `PlayerActivity.setupChapters` → `ChapterUtils.mergeChapters`. |
| `mpvArgs` | ✅ YES | `PlayerActivity.setVideo` → 5th arg of mpv `loadfile`. Re-applied from mpv metadata for downloads. |
| `initialized` | ✅ YES | `HosterLoader.getResolvedVideo` — gates whether `resolveVideo` is called. |
| `resolution` | ❌ no | Not read by player UI (reserved for future sort). |
| `bitrate` | ❌ no | Not read by player UI. |
| `ffmpegStreamArgs` | ❌ no (player) | Used by download pipeline only. |
| `ffmpegVideoArgs` | ❌ no (player) | Used by download pipeline only. |
| `internalData` | ❌ no (player) | Round-tripped via `SerializableVideo` for downloads. |

> **Note on `Video.status` (the `@Transient @Volatile var status: State` field):** the **app's
> runtime** `Video` has a `status: State` field (`QUEUE/LOAD_VIDEO/READY/ERROR`), BUT the app does
> NOT read `video.status` from your extension's object. It tracks its OWN parallel
> `List<Video.State>` per hoster (`HosterState.Ready.videoState` in `QualitySheet.kt:58`).
> **Setting `video.status` from extension code has no effect on the UI.** And anyway, the published
> v16 `Video` doesn't even have `status` — so you can't set it at compile time. Just ignore it.

---

## 5. `Hoster` fields the app actually reads

| Field | Read? | Where / how |
|---|---|---|
| `hosterUrl` | ✅ YES | `videoListRequest(hoster)` default does `GET(hoster.hosterUrl, headers)`. |
| `hosterName` | ✅ YES | Quality sheet header label. |
| `videoList` | ✅ YES | If non-null, app uses these videos directly (skips `getVideoList(hoster)`). |
| `lazy` | ✅ YES | If true, app shows "Tap to load" (`HosterState.Idle`), defers fetch until user expands. |
| `internalData` | ✅ YES | Round-tripped via `SerializableHoster` for persistence. |
| `NO_HOSTER_LIST` sentinel | ✅ YES | App detects legacy flat-list wrapping; renders without hoster headers. |

> Same as Video: the app's runtime `Hoster` has a `status: State` field, but the app tracks its own
> `HosterState` enum and doesn't read `hoster.status`. The published v16 `Hoster` doesn't even have
> `status` or `copy()`. Ignore it.

---

## 6. The auto-selection logic (`HosterLoader.selectBestVideo`)

`HosterLoader.selectBestVideo(hosterState: List<HosterState>): Pair<Int, Int>` (`HosterLoader.kt:27-67`):
1. Consider only `HosterState.Ready` hosters.
2. First, look for a video with `preferred == true` AND state `READY` or `QUEUE`.
3. Else, first video with `videoUrl.isNotEmpty()` AND state `READY` or `QUEUE`.
4. Return `(-1, -1)` if nothing viable.

So if you set `preferred = true` on your best quality, it auto-plays first. The order from
`sortHosters()` / `sortVideos()` is respected because the app iterates in list order.

### Failure handling

When `resolveVideo` returns `null` OR the resolved `videoUrl` is empty:
- `PlayerViewModel.loadVideo` (`PlayerViewModel.kt:1399-1418`) marks that video's state `ERROR` and
  recursively calls `selectBestVideo` again on the updated state.
- So the iteration is: pick best → try resolve → on failure mark ERROR + pick next best → repeat.

This means **if `resolveVideo` fails for one video, the app automatically falls back to the next
preferred/available video**. You don't need to handle fallback inside `resolveVideo` — just return
`null` on failure.

---

## 7. Subtitles, audio tracks, timestamps — concrete wiring

`PlayerActivity.setupTracks` (`PlayerActivity.kt:1241-1263`), called from `fileLoaded()` after mpv
reports the file loaded:
```kotlin
audioTracks?.forEach    { executeMPVCommand(arrayOf("audio-add", audio.url, "auto", audio.lang)) }
subtitleTracks?.forEach { executeMPVCommand(arrayOf("sub-add",  sub.url,   "auto", sub.lang))   }
```
- `Track.url` → the mpv track URL.
- `Track.lang` → becomes the mpv track title (shown in the audio/subtitle picker).
- These are **external side-loaded** tracks, not embedded.

`PlayerActivity.setupChapters` (`PlayerActivity.kt:1265-1288`) → `ChapterUtils.mergeChapters`:
reads `TimeStamp.start` (seconds, Double), `end`, `name`, `type` (for color/label). Merged with
mpv's own chapter list.

---

## 8. Practical implications for writing an extension

1. **Set `videoUrl` to a real, directly-playable URL** (m3u8, mp4) when you have it at
   `videoListParse` time. Only use `resolveVideo` for URLs that expire or require a per-play fetch.
2. **Set `preferred = true` on exactly one video per hoster** (the best quality / user-preferred) so
   auto-play picks it.
3. **Set `headers`** with `Referer`/`Origin` if the host requires it — otherwise mpv's request 403s.
   `video.headers ?: source.headers` is the fallback chain.
4. **Use `videoTitle` for the quality label** (e.g. `"1080p - Vidmoly"` or `"Sub | 720p"`). This is
   what shows in the quality sheet. Do NOT try to use a `quality` field (doesn't exist in v16).
5. **`sortVideos()`** should reorder by the user's quality preference (read from your
   `SharedPreferences`). Remember: use `it.videoTitle.contains(quality)`, NOT `it.quality.contains`
   (the ext-lib KDoc example is stale).
6. **`sortHosters()`** should reorder hosters by the user's server preference.
7. **Sub/dub** (project rule §7, §8): the site has 3 audio types (SUB, HSUB, DUB). Show availability
   via `SEpisode.scanlator` (e.g. `"SUB • DUB"`), NOT in the episode name. At the video level,
   label each `Video.videoTitle` clearly with its audio type (e.g. `"DUB - 1080p"`).
8. **Lazy hosters**: if a site has many servers and fetching all is slow/rate-limited, set
   `hoster.lazy = true` + `videoList = null` so the app only fetches when the user expands. Return
   `hosterUrl` so `videoListRequest(hoster)` (default `GET hoster.hosterUrl`) works, OR override
   `videoListRequest(hoster)` for custom requests.
9. **Deduplication** (rule §7): the site shares tokens across audio types (same video served under
   SUB/HSUB/DUB labels). Dedup in your `videoListParse` — don't return 3 identical `videoUrl`s.
   If they're genuinely the same stream, keep one `Video` and label its audio type in `videoTitle`.
10. **`resolveVideo` pattern for protected streams**: return `Video(videoUrl = "", internalData =
    "<token-or-id>", initialized = false)` from `videoListParse`, then in `resolveVideo` use
    `video.internalData` to fetch the real URL and return `video.copy(videoUrl = realUrl, initialized
    = true)`. The app calls `resolveVideo` only once per video (then `initialized = true` skips it).

---

## 9. Things that surprised me (honest notes)

- **No ExoPlayer at all.** If you've worked with Tachiyomi/Mihon manga extensions, note Aniyomi's
  anime side is fully mpv. There are no `MediaSource` factories to hook.
- **`Video.status` is a red herring.** It looks like you should set it, but the app ignores your
  value and tracks its own. Don't bother.
- **The `"null"` string literal** is a real sentinel in the legacy bridge. Don't accidentally set
  `videoUrl = "null"` — use `""` for "unresolved, let resolveVideo handle it".
- **`ParsedAnimeHttpSource` is deprecated** and the v16 published version lacks hoster/video selector
  hooks. The app's runtime version HAS them, but you can't compile against them. Extend
  `AnimeHttpSource` directly.
- **The ext-lib's own `sortVideos` KDoc example uses `it.quality`** which doesn't exist in v16. This
  is a documentation bug in the official ext-lib. Use `it.videoTitle`.
