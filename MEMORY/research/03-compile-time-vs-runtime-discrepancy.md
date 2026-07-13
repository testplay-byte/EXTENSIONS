# Compile-Time (published ext-lib v16) vs Runtime (app source-api) Discrepancy

> Last updated: 2026-06-22 · Status: VERIFIED
>
> Extensions `compileOnly`-depend on the **published** `aniyomiorg/extensions-lib:v16` (a STUB
> library — every method body throws `Exception("Stub!")`). The Aniyomi app ships its **own** fuller
> implementation of the same classes in `source-api/`. The two are NOT identical. **You can only use
> fields/methods that exist in the PUBLISHED v16 at compile time**, even though the app has more at
> runtime. This file documents the exact differences so you don't write code that compiles against a
> runtime-only field by mistake.

---

## 1. Why the discrepancy exists (verified)

- Published lib: `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/` — every method body is
  `throw Exception("Stub!")`. Class declarations are minimal.
- App runtime: `SHARED/REFERENCE_HUB/aniyomi-app/source-api/src/commonMain/kotlin/` — real impls, fuller
  classes (extra fields, `copy()` helpers, `@Transient` state, legacy compat getters).
- The app's `source-api` `library/build.gradle.kts` even has a `dokkatoo` `sourceLink` pointing its
  `animesource/` docs at `github.com/aniyomiorg/aniyomi/tree/master/source-api/...` — confirming the
  app's source-api IS the canonical runtime, and the published lib is a stub subset.

The stub-only-published design exists so extensions don't accidentally call real logic at
compile/test time (which would fail without the app's DI graph). You compile against stubs; the app
injects real impls at runtime via classloader.

---

## 2. `Video` — published v16 vs runtime

| Member | Published v16 (`model/Video.kt`) | Runtime app (`source-api/.../model/Video.kt`) | Usable in extension code? |
|---|---|---|---|
| `videoUrl: String` | ✅ `val` (var default `""`) | ✅ `var` | ✅ yes |
| `videoTitle: String` | ✅ `val` | ✅ `val` | ✅ yes |
| `resolution`, `bitrate`, `headers`, `preferred`, `subtitleTracks`, `audioTracks`, `timestamps`, `mpvArgs`, `ffmpegStreamArgs`, `ffmpegVideoArgs`, `internalData`, `initialized` | ✅ all `val` | ✅ all `val` | ✅ yes |
| `status: State` (`QUEUE/LOAD_VIDEO/READY/ERROR`) | ❌ **ABSENT** | ✅ `@Transient @Volatile var` (lines 86-91) | ❌ **NO — cannot set/read from extension** |
| `url: String` (page URL) | ❌ **ABSENT** | ✅ deprecated getter returns private `videoPageUrl` | ❌ **NO** |
| `quality: String` | ❌ **ABSENT** | ✅ deprecated getter returns `videoTitle` | ❌ **NO — use `videoTitle`** |
| Legacy ctor `Video(url, quality, videoUrl, …)` | ⚠️ `@Deprecated(level = ERROR)` — **won't compile** | ✅ present (deprecated) | ❌ **NO — use named-arg ctor** |
| `SerializableVideo` companion (serialize/toVideoList) | ❌ absent from published | ✅ present | ❌ NO (app-internal) |
| `MPV_ARGS_TAG` const | ❌ absent | ✅ present | ❌ NO (app-internal) |
| `data class` (so `copy()` works) | ✅ yes | ✅ yes | ✅ yes — use `video.copy(...)` |

**Practical takeaway:** in extension code, construct `Video` with named args only:
```kotlin
Video(
    videoUrl = "https://.../master.m3u8",
    videoTitle = "1080p - Vidmoly",
    headers = headers,
    preferred = true,
)
```
Never `Video(url, quality, videoUrl, …)` (won't compile) and never `video.status = ...` (no such
field at compile time).

---

## 3. `Hoster` — published v16 vs runtime

| Member | Published v16 (`model/Hoster.kt`) | Runtime app | Usable in extension code? |
|---|---|---|---|
| `hosterUrl`, `hosterName`, `videoList`, `internalData`, `lazy` | ✅ (constructor `val`s) | ✅ | ✅ yes |
| `status: State` (`IDLE/LOADING/READY/ERROR`) | ❌ **ABSENT** | ✅ `@Transient @Volatile var` | ❌ **NO** |
| `copy(...)` | ❌ **ABSENT** (plain `class`, not `data class`) | ✅ has a manual `copy()` | ❌ **NO — construct a new Hoster to "modify"** |
| `open` (subclassable) | ❌ **NO** (`class`, not `open class`) | ✅ `open class` | ❌ NO — cannot subclass |
| `NO_HOSTER_LIST` const + `toHosterList()` | ✅ | ✅ | ✅ yes |

**Practical takeaway:** to "modify" a Hoster, build a new one:
```kotlin
val updated = Hoster(
    hosterUrl = orig.hosterUrl,
    hosterName = orig.hosterName,
    videoList = newVideoList,
    internalData = orig.internalData,
    lazy = orig.lazy,
)
```

---

## 4. `AnimeHttpSource` — published v16 vs runtime

| Method | Published v16 (stub) | Runtime app | Notes |
|---|---|---|---|
| `getHosterList(episode)` | ✅ stub | ✅ real default (`client.newCall(hosterListRequest(episode)).awaitSuccess().let { hosterListParse(it) }`) | You implement `hosterListParse`. |
| `hosterListRequest(episode)` | ✅ stub default `GET(baseUrl + episode.url, headers)` | ✅ same real default | Override only if custom request. |
| `hosterListParse(response)` | ✅ abstract | ✅ abstract | **You MUST implement.** |
| `getVideoList(hoster)` | ✅ stub | ✅ real default | You implement `videoListParse(response, hoster)`. |
| `videoListRequest(hoster)` | ✅ stub default `GET(hoster.hosterUrl, headers)` | ✅ same | Override if custom. |
| `videoListParse(response, hoster)` | ✅ `open` stub | ✅ abstract | You MUST override. |
| `resolveVideo(video)` | ✅ stub | ✅ real default `return video` (no-op) | Override ONLY for lazy URL resolution. |
| `List<Hoster>.sortHosters()` | ✅ `open` real default `return this` | ✅ same | Override for user pref. |
| `List<Video>.sortVideos()` | ✅ `protected open` stub | ✅ `open` real default → delegates to deprecated `sort()` → identity | Override for user pref. **Use `it.videoTitle`, NOT `it.quality`.** |
| `getSeasonList`/`seasonListRequest`/`seasonListParse` | ✅ (16) | ✅ | Implement or throw `UnsupportedOperationException`. |
| Legacy `getVideoList(episode)`/`videoListParse(response)`/`videoUrlParse`/`videoListSelector`/`videoFromElement` | ❌ **ABSENT from published v16 interface** | ✅ present (deprecated, for old installed extensions) | ❌ **Cannot use in v16 extensions — won't compile.** |
| `getVideoUrl`/`videoUrlRequest`/`videoUrlParse` | ✅ `@Deprecated` present | ✅ `@Deprecated` present | Don't use — use `resolveVideo`. |

---

## 5. `ParsedAnimeHttpSource` — published v16 vs runtime

| Feature | Published v16 | Runtime app |
|---|---|---|
| `@Deprecated` annotation | ✅ yes | ✅ yes |
| `popularAnime*`/`searchAnime*`/`latestUpdates*` selector hooks | ✅ | ✅ |
| `animeDetailsParse(document)`, `episodeListSelector`/`episodeFromElement` | ✅ | ✅ |
| `seasonListSelector`/`seasonFromElement` | ✅ (16) | ✅ (16) |
| `hosterListSelector`/`hosterFromElement` | ❌ **ABSENT** | ✅ present (16) |
| `videoListSelector`/`videoFromElement`/`videoUrlParse(document)` | ❌ **ABSENT** | ✅ present |

**Practical takeaway:** if you extend `ParsedAnimeHttpSource` on v16, you get NO hoster/video
selector helpers — you still implement `hosterListParse` and `videoListParse(response, hoster)`
yourself with Jsoup. **Recommendation: extend `AnimeHttpSource` directly** for new v16 extensions.

---

## 6. `AnimeSource` interface — published v16 vs runtime

| Method | Published v16 | Runtime app |
|---|---|---|
| `id`, `name` | ✅ | ✅ |
| `getAnimeDetails`, `getEpisodeList`, `getSeasonList`, `getHosterList`, `getVideoList(hoster)` | ✅ all suspend | ✅ all suspend |
| `getVideoList(episode: SEpisode)` | ❌ **ABSENT** | ✅ present (deprecated default via Rx `fetchVideoList`) |
| `fetchAnimeDetails`/`fetchEpisodeList`/`fetchVideoList` (Rx) | ❌ **ABSENT** | ✅ `@Deprecated` |

So the v16 interface is **cleaner** — only the new suspend methods, no Rx legacy.

---

## 7. Extension-loader version compatibility — VERIFIED

> This is the rule that decides whether the app will even LOAD your built APK.

**File:** `SHARED/REFERENCE_HUB/aniyomi-app/app/src/main/java/eu/kanade/tachiyomi/extension/anime/util/AnimeExtensionLoader.kt`

```kotlin
companion object {
    const val LIB_VERSION_MIN = 12       // line 47
    const val LIB_VERSION_MAX = 16       // line 48
}
// ...
val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()    // line 254
if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
    logcat(LogPriority.WARN) { "Lib version is $libVersion, while only versions $LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed" }
    return AnimeLoadResult.Error                                          // line 260
}
```

**Rules:**
1. The app reads `versionName` from the APK's `PackageInfo`.
2. It takes the prefix before the last `.` and parses as a `Double`.
   - `"16.52"` → `16.0` ✅
   - `"14.29"` → `14.0` ✅ (still accepted — 12 ≤ 14 ≤ 16)
   - `"17.0"` → `17.0` ❌ REJECTED (> 16)
   - `"11.0"` → `11.0` ❌ REJECTED (< 12)
3. **Our ext-lib 16 extensions MUST have `versionName = "16.<versionCode>"`.** This is set by the
   build-logic (`versionName = "<major>.$versionCode"`). The yuzono build-logic hard-codes `"14."`;
   ours must use `"16."`.
4. **Update detection** (`AnimeExtensionManager.kt:379`): an update exists if
   `available.versionCode > installed.versionCode OR available.libVersion > installed.libVersion`.
   So bumping the lib major also flags an update.

### Signature trust (also verified)

`AnimeExtensionLoader.kt:263-274` — after the version check, the app calls
`trustExtension.isTrusted(pkgInfo, signatures)`. If NOT trusted, the extension is loaded as
`AnimeExtension.Untrusted` and the **user must manually enable "Untrusted extensions" in Aniyomi
settings** to use it. Our debug-signed APKs will be Untrusted. This is expected and fine for testing —
just document it for the user.

---

## 8. Java/bytecode compatibility — VERIFIED, with a caveat

**The published v16 jar is compiled with Java 17 bytecode** (class file version 61):
- `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/.jitpack.yml`: `jdk: openjdk17`
- `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/library/build.gradle.kts`: `JavaVersion.VERSION_17` +
  `JvmTarget.JVM_17`, `compileSdk = 36`.
- `.jitpack.yml` install step: `./gradlew build :library:publishToMavenLocal`.

**The yuzono reference build-logic uses Java 11** (via `tapmoc.configureJavaCompatibility(11)` in
`gradle/build-logic/src/main/kotlin/keiyoushi/gradle/configurations/Kotlin.kt`).

**Problem:** Java 11 `javac`/`kotlinc` cannot read Java 17 class files (version 61 > 55) when
resolving a `compileOnly` dependency. So a Java 11 build consuming the v16 jar will likely fail at
compile time with "unsupported class file version".

**Resolution options for our extensions build (in priority order):**
1. **Use Java 17 in our build-logic** (set `kei.versions.java = 17`, drop or keep `tapmoc` for AGP
   compat). Cleanest. Android `minSdk = 21` is fine because Java 17 source can target Java 8/11
   bytecode via `--release` or sourceCompat/targetCompat — the EXTENSION's own bytecode doesn't need
   to be 17, only the toolchain that reads the v16 stub jar.
2. **Keep Java 11 + `includeBuild` the ext-lib source** instead of the JitPack jar. Recompiles the
   stubs with our Java. Adds the ext-lib as a composite build. Works but couples us to ext-lib source.
3. **Keep Java 11 + tapmoc** and hope it desugars/translates the stubs. UNVERIFIED — tapmoc's
   `configureJavaCompatibility` configures source/target compat, not class-file-version translation.
   I doubt it bridges 17→11. Don't rely on this.

**Recommendation:** Option 1 (Java 17 toolchain) for our extensions. This is an OPEN VERIFICATION
ITEM — confirm at the first real build. If Java 17 breaks something, fall back to Option 2
(`includeBuild`).

> NOTE: the ext-lib's OWN `libs.versions.toml` uses AGP 8.12.0 / Kotlin 2.2.0 / serialization 1.9.0,
> but that's the lib's build tooling — irrelevant to consumers. Consumers (us) need only: a JDK that
> can read Java 17 class files (JDK 17+) + AGP + Kotlin compatible with our chosen Android setup.

---

## 9. Summary — what's safe to use in extension code (v16)

✅ **Use freely:**
- `Video(...)` with named-arg constructor; `video.copy(...)`
- `Hoster(...)` constructor (no subclass, no copy)
- `Track`, `TimeStamp`, `ChapterType`, `FetchType`
- `SAnime.create()` / `SEpisode.create()` + all their `var` fields (`fetch_type`, `season_number`, `scanlator`, …)
- All `AnimeHttpSource` abstract/open methods (hoster flow, seasons, resolveVideo, sortHosters, sortVideos)
- `ConfigurableAnimeSource.setupPreferenceScreen`, `ResolvableAnimeSource`, `AnimeSourceFactory`
- `keiyoushi.utils.*` from `core/` (preferences, JSON, network, coroutines, crypto, …)

❌ **Do NOT use (won't compile or no effect):**
- `Video(url, quality, videoUrl, …)` legacy constructor (deprecated to ERROR)
- `video.quality`, `video.url`, `video.status` (absent from v16)
- `hoster.status`, `hoster.copy(...)` (absent from v16)
- Legacy `getVideoList(episode)` / `videoListParse(response)` / `videoUrlParse` / `videoListSelector` / `videoFromElement` (absent from v16 interface)
- `ParsedAnimeHttpSource`'s `hosterListSelector`/`videoListSelector` (absent from v16 published)
- `it.quality.contains(...)` in `sortVideos()` (the ext-lib KDoc example is stale — use `it.videoTitle`)
