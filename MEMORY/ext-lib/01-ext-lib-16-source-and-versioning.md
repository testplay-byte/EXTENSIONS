# ext-lib 16 — Versioning, Sources & How to Depend on It

> Last updated: 2026-06-22 · Status: VERIFIED (against live GitHub + source code)
>
> **This is the single most important file in the ext-lib/ folder.** It answers the user's flagged
> concern: "The pinned aniyomi-extensions-lib version (bdc8184127) may NOT have all ext-lib 16
> features." It does NOT. Here's exactly what's going on and what to do.

---

## 1. TL;DR (the decision)

**To use ext-lib 16 (Hoster, TimeStamp, sortHosters, sortVideos, resolveVideo, FetchType, seasons),
your extension's `build.gradle` MUST depend on the OFFICIAL `aniyomiorg/extensions-lib` at tag `v16`,
NOT the komikku fork that the yuzono reference repo uses.**

```groovy
dependencies {
    compileOnly 'com.github.aniyomiorg:extensions-lib:v16'
}
```

The yuzono reference repo (`SHARED/REFERENCE_HUB/anime-extensions-ref`) is pinned to:
```groovy
compileOnly 'com.github.komikku-app:aniyomi-extensions-lib:bdc8184127'   // ← ext-lib 14 era, NO Hoster/TimeStamp/resolveVideo
```
**That fork is stale for our purposes.** It compiles, but you cannot use any ext-lib 16 API with it.

---

## 2. The four ext-lib repos that exist (verified 2026-06-22)

| Repo (github.com/…) | HEAD / tag | `libVersion` | Has Hoster? | Has resolveVideo/sortHosters/sortVideos? | `@since extensions-lib 16` count | Use for our project? |
|---|---|---|---|---|---|---|
| **`aniyomiorg/extensions-lib`** | tag `v16` @ `782a5a6b` (annotated); tag `16` @ `9822a39e` | `v16` | ✅ | ✅ | 21 | **✅ YES — this is the one** |
| `komikku-app/aniyomi-extensions-lib` | `bdc8184127` (latest, = what yuzono uses) | (14 era) | ❌ | ❌ | 0 | ❌ no (stale) |
| `komikku-app/extensions-lib` | `554a1b70` | `1.4.5.1` | ❌ | ❌ | 0 | ❌ no (stale) |
| `keiyoushi/extensions-lib` | `18a8e26b` | `1.4.5` | ❌ | ❌ | 0 | ❌ no (stale) |

All four are cloned under `SHARED/REFERENCE_HUB/` for comparison:
- `SHARED/REFERENCE_HUB/ext-lib-aniyomiorg/` — **the authoritative ext-lib 16** (full clone, has all tags)
- `SHARED/REFERENCE_HUB/aniyomi-extensions-lib/` — komikku fork @ bdc8184127 (what yuzono compiles against)
- `SHARED/REFERENCE_HUB/ext-lib-keiyoushi/` — keiyoushi fork (shallow)
- `SHARED/REFERENCE_HUB/ext-lib-komikku-new/` — komikku "new" fork (shallow)

> NOTE: `aniyomiorg/aniyomi-extensions-lib` (with the "aniyomi-" prefix) returns HTTP 404 — that repo
> does NOT exist. The official one is `aniyomiorg/extensions-lib` (no prefix). The README inside the
> komikku fork still says `com.github.aniyomiorg:extensions-lib` — that's correct; the fork just
> republishes under its own group `com.github.komikku-app:aniyomi-extensions-lib`.

---

## 3. Tags on the official `aniyomiorg/extensions-lib`

```
1.0, 1.1, 1.2, 1.3, 13, 14, 16, 16-rc1, 16-rc2, 16-rc3, 16-rc4, v16
```

- Tag `16` → commit `9822a39e` "feat: Add new parameters & small change for seasons (#20)"
- Tag `v16` → annotated tag → commit `782a5a6b` (same tree as `16` plus the readme bump commit `59418e2`)
- `main` HEAD = `59418e2` "Bump libversion in readme (#21)" — slightly ahead of `v16` tag, but no API
  difference for our purposes (only a README change).
- The ext-lib's own `library/build.gradle.kts` has `val ver = "16-rc4"` even on the `v16` tag — this
  is the internal `moduleVersion` for Dokka docs only; the JitPack coordinate is `:v16`.

**Use tag `v16`.** That's what the official README instructs (`libVersion = 'v16'`).

---

## 4. ext-lib version history (what "16" means)

`@since extensions-lib X` annotations in the official ext-lib source map to:

| Version | Key additions |
|---|---|
| 1.0 – 1.3 | Original `AnimeSource`/`AnimeHttpSource`/`Video`/`SEpisode`/`SAnime` (legacy `getVideoList(episode)`, `videoListParse(response)`, `videoUrlParse`) |
| 1.4 | `UpdateStrategy`, `getAnimeUrl`, `getEpisodeUrl`, `ResolvableAnimeSource` |
| 1.5 | `ConfigurableSource`, `CatalogueSource` suspend methods, `getVideoUrl`/`videoUrlRequest`/`videoUrlParse` per-video resolver |
| 13 | (tag exists; pre-Aniyomi-split era) |
| 14 | `generateId(name, lang, versionId)`; `getRelatedAnimeList` (manga side); the **komikku fork `bdc8184127` is at this era** |
| **16** | **The new Hoster-based pipeline:** `Hoster`, `getHosterList`/`hosterListRequest`/`hosterListParse`, `getVideoList(hoster)`/`videoListRequest(hoster)`/`videoListParse(response, hoster)`, `resolveVideo`, `sortHosters`, `sortVideos`, `FetchType` (Seasons/Episodes), `getSeasonList`/`seasonListRequest`/`seasonListParse`, `SAnime.fetch_type`, `SAnime.season_number`, `Video.resolution`/`bitrate`/`videoTitle`/`mpvArgs`/`ffmpegStreamArgs`/`ffmpegVideoArgs`/`internalData`/`initialized`, `TimeStamp`, `Track`, `ChapterType`. Legacy `getVideoList(episode)` + `videoUrlParse` are `@Deprecated`. |

There is **no tag `15`**. The jump from 14 → 16 is the Aniyomi Hoster refactor. "ext-lib 16" and
"the Hoster API" are the same thing.

---

## 5. How to depend on ext-lib 16 in a Gradle Kotlin DSL project

### Option A — version catalog (recommended; matches yuzono's structure)

In `gradle/libs.versions.toml`:
```toml
[versions]
# ...
[libraries]
aniyomi-lib = { module = "com.github.aniyomiorg:extensions-lib", version = "v16" }
[bundles]
common = ["aniyomi-lib", "okhttp", "jsoup", "kotlin-json", "kotlin-protobuf", "kotlin-json-okio", "coroutines-core", "coroutines-android", "injekt-core", "rxjava", "quickjs"]
```
Then in an extension's `build.gradle.kts`:
```kotlin
dependencies {
    compileOnly(libs.bundles.common)
    implementation(project(":core"))
    // …hoster-specific lib modules as implementation(project(":lib:<name>"))
}
```

### Option B — direct Groovy (matches yuzono's per-extension `build.gradle`)

```groovy
dependencies {
    compileOnly 'com.github.aniyomiorg:extensions-lib:v16'
}
```

### JitPack setup (root `settings.gradle.kts`)

JitPack must be in `dependencyResolutionManagement.repositories`:
```kotlin
repositories {
    google()
    mavenCentral()
    maven(url = "https://www.jitpack.io")
}
```
(The yuzono repo already has this — see `SHARED/REFERENCE_HUB/anime-extensions-ref/settings.gradle.kts`.)

---

## 6. CRITICAL: the published ext-lib v16 is a STUB library

**Every method body in the published `aniyomiorg/extensions-lib:v16` throws `Exception("Stub!")`.**
This is by design: extensions `compileOnly`-depend on the lib (so they compile against the API),
and the **Aniyomi app provides the real implementations at runtime** (the app's `source-api/` module
contains the fuller, non-stub versions of the same classes).

Consequences:
- You **cannot run an extension in a unit test** against the published lib — calling any method
  throws `Stub!`. Testing happens on-device via the real Aniyomi app.
- The published lib's classes are a **subset** of the app's runtime classes. See
  `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` for the exact differences. **You can
  only use fields/methods that exist in the PUBLISHED v16 lib at compile time**, even though the app
  has more at runtime.

---

## 7. What changed from ext-lib 14 (yuzono) → ext-lib 16 (ours)

If you copy a yuzono extension as a starting point, you MUST make these changes to move it to
ext-lib 16:

1. **Dependency**: `com.github.komikku-app:aniyomi-extensions-lib:bdc8184127`
   → `com.github.aniyomiorg:extensions-lib:v16`.
2. **`versionName`**: the build-logic currently hard-codes `"14.$versionCode"`. Change to
   `"16.$versionCode"` (in `PluginExtensionLegacy.kt` or our equivalent).
3. **Legacy `getVideoList(episode)` + `videoListParse(response)` + `videoUrlParse`** → replace with
   the Hoster pipeline: `hosterListParse(response)`, `videoListParse(response, hoster)`, and
   `resolveVideo(video)` for lazy URL resolution.
4. **`Video` constructor**: the legacy `Video(url, quality, videoUrl, headers, …)` constructor is
   `@Deprecated(level = ERROR)` in v16 — it will NOT compile. Use the new
   `Video(videoUrl = …, videoTitle = …, …)` constructor.
5. **`Video.quality` / `Video.url`** getters: gone in v16. Use `videoTitle` for the label.
   (`videoUrl` is the stream URL; there is no separate "page URL" field in v16.)
6. **`List<Video>.sort()`** (the old ext-lib 14 helper used by yuzono's `core/Source.kt`) → override
   `List<Video>.sortVideos()` (ext-lib 16 name). The old `sort()` is gone in v16.
7. **`ParsedAnimeHttpSource`**: `@Deprecated` in v16 and lacks hoster/video selector hooks. Prefer
   extending `AnimeHttpSource` directly and implementing parse methods yourself with Jsoup. (You CAN
   still extend `ParsedAnimeHttpSource`, but you get no hoster/video selector helpers from it.)
8. **`core/Source.kt`** (yuzono's base class): its `TODO: Remove with ext lib 16` commented-out
   legacy overrides can finally be deleted. It throws `UnsupportedOperationException` for legacy
   methods — on ext-lib 16 those methods don't exist in the interface, so the overrides are moot.
9. **`FetchType`**: set `SAnime.fetch_type = FetchType.Episodes` (default) for normal anime, or
   `FetchType.Seasons` for sites that organize by seasons (then implement `seasonListParse`).

See `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` for the full field-by-field diff.

---

## 8. How to verify the ext-lib version yourself (don't trust this doc blindly)

```bash
# 1. Check which features a given ext-lib checkout has:
cd SHARED/REFERENCE_HUB/ext-lib-aniyomiorg
grep -rln "class Hoster" library/src          # should find model/Hoster.kt
grep -rln "sortHosters" library/src           # should find online/AnimeHttpSource.kt
grep -rc "since extensions-lib 16" library/src | grep -v ':0$'   # should list ~6 files, ~21 hits

# 2. Check the tag a JitPack coordinate resolves to:
git tag --points-at v16      # shows tags on that commit
git log --oneline -1 v16     # shows the commit

# 3. Confirm the published artifact group:
grep -m1 "group" library/build.gradle.kts     # group = "com.github.aniyomiorg"
grep -m1 "version" library/build.gradle.kts   # version = ver ("16-rc4" internally; use tag 'v16' for JitPack)
```

---

## 9. Open questions / things to verify later

- **Does JitPack actually serve `com.github.aniyomiorg:extensions-lib:v16`?** The tag exists and the
  README says to use it, but we have NOT yet done a real Gradle build to confirm JitPack resolves it.
  This will be verified when we set up `EXTENSIONS/` and run the first build. If JitPack fails, the
  fallback is to `includeBuild()` the ext-lib source directly (we have it cloned).
- **The ext-lib's `build.gradle.kts` uses Java 17 / compileSdk 36**, but the yuzono build-logic uses
  **Java 11 / compileSdk 34** via `tapmoc`. When we set up our own build, we must reconcile this
  (likely: keep Java 11 for extensions via tapmoc, since the ext-lib is `compileOnly` and its
  bytecode target is what matters — need to verify the published v16 jar targets Java 11 or 17).
- **`tapmoc`** (`com.gradleup.tapmoc:0.4.2`) is a plugin used by yuzono's build-logic to allow
  compiling extensions against a newer AGP/Java while targeting an older runtime. We need to
  understand it before finalizing our build config. (See
  `MEMORY/research/02-reference-extension-build-and-structure.md`.)
