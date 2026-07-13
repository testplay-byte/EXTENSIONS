# Guide: How to Create a New ext-lib 16 Extension

> Last updated: 2026-06-22 · Status: VERIFIED (derived from yuzono `src/en/tokuzilla/` + ext-lib 16
> API; the actual first build is a pending verification item).
>
> Step-by-step: from "I want to support site X" to "built APK ready to test". This assumes the
> build environment is already set up per `MEMORY/guides/01-build-setup-for-ext-lib-16.md`
> (JDK 17 + Android SDK + `.android-env.sh`).

---

## 0. When to use this guide

This is the **implementation** guide — it assumes the site has ALREADY been analyzed (per the
workflow folder's site-analysis steps, to be written when we build the first extension). You should
arrive here with:
- The target site URL + language.
- Verified URL structure (home, search, anime detail, episode list, watch/embed).
- Verified server-list paths (ALL of them tested via agent-browser).
- Verified audio-type mapping (SUB / HSUB / DUB — project rule §7).
- Verified video extraction approach (which `lib/` extractor or custom logic).

If any of that is missing, go back to the site-analysis workflow first.

---

## 1. Scaffold the extension folder

Copy the template to create a new self-contained extension workspace:

```bash
cd /home/z/my-project/extensions
cp -r _template <name>          # lowercase, no hyphens (package-name convention)
```

Then scaffold the Gradle project inside `<name>/DEV/`. The easiest path is to copy the build system
from an existing extension (e.g. `anikoto/DEV/`) and adapt the package, extClass, and source. The
resulting folder structure (see `EXTENSIONS/README.md`):

```
EXTENSIONS/<name>/
├── EXTENSION.md          ← fill in identity, build, status
├── DEV/                  ← Gradle project (scaffold here)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle/libs.versions.toml
│   ├── common/{AndroidManifest.xml, proguard-rules.pro}
│   ├── stubs/            ← ext-lib v16 stubs (compileOnly)
│   └── src/<lang>/<name>/
│       ├── build.gradle.kts
│       └── src/main/{kotlin/.../<Name>.kt, res/mipmap-*/ic_launcher.png, AndroidManifest.xml}
├── APK/                  ← built APKs land here (copies)
├── ANALYSIS/
└── MEMORY/               ← knowledge base (session-logs, sites, issues, modules, ...)
```

Inside the Gradle source tree (`DEV/src/<lang>/<name>/src/`), the Kotlin files live directly under
`src/` (NOT `src/main/kotlin/`) — the build-logic sets `sourceSets["main"].java.srcDirs = "src"`.
So the main class is at `DEV/src/<lang>/<name>/src/eu/kanade/tachiyomi/animeextension/<lang>/<name>/<ClassName>.kt`.

- No `AndroidManifest.xml` (shared one is used) — UNLESS you ship a deep-link `*UrlActivity` (then
  add a local one, see §8).
- No `strings.xml`, no `assets/` unless needed.

---

## 2. `build.gradle` (Groovy)

```groovy
ext {
    extName = 'SiteName'              // human-readable, ASCII only (asserted by PluginExtensionLegacy)
    extClass = '.SiteName'            // MUST start with a dot; matches the class name in §3
    extVersionCode = 1                // bump on each release
    isNsfw = false                    // true if 18+; sets manifest meta-data nsfw=1
}
apply plugin: "kei.plugins.extension.legacy"

dependencies {
    // Add host extractors / helpers you need:
    // implementation(project(':lib:playlistutils'))
    // implementation(project(':lib:cryptoaes'))
    // implementation(project(':lib:cloudflareinterceptor'))
}
```

For a **multisrc** extension (reusing a theme like `zorotheme`):
```groovy
ext {
    extName = 'SiteName'
    extClass = '.SiteName'
    themePkg = 'zorotheme'            // triggers evaluationDependsOn(":lib-multisrc:zorotheme")
    overrideVersionCode = 1           // versionCode = theme.baseVersionCode + this
    baseUrl = 'https://sitename.tld'  // sets SOURCEHOST/SOURCESCHEME manifest placeholders
    isNsfw = false
}
apply plugin: "kei.plugins.extension.legacy"
dependencies {
    // theme is auto-added as implementation(theme) by PluginExtensionLegacy when themePkg is set
    implementation(project(':lib:cryptoaes'))
    implementation(project(':lib:playlistutils'))
}
```

---

## 3. The source class — `src/eu/kanade/tachiyomi/animeextension/<lang>/<name>/<ClassName>.kt`

**Package convention:** `eu.kanade.tachiyomi.animeextension.<lang>.<name>` (verified — yuzono uses
this; the namespace + applicationIdSuffix produce `eu.kanade.tachiyomi.animeextension.<lang>.<name>`).

**Base class:** extend `AnimeHttpSource` directly (NOT `ParsedAnimeHttpSource` — it's `@Deprecated`
on v16 and lacks hoster/video selector hooks; see
`MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` §5).

### Minimal skeleton (ext-lib 16)

```kotlin
package eu.kanade.tachiyomi.animeextension.en.sitename

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class SiteName : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "SiteName"
    override val baseUrl = "https://sitename.tld"
    override val lang = "en"
    override val supportsLatest = true   // false if site has no "latest updates" page

    private val preferences by getPreferencesLazy()

    // ── Catalogue: popular ──────────────────────────────────────────────
    override fun popularAnimeRequest(page: Int): Request { /* GET popular page */ }
    override fun popularAnimeParse(response: Response): AnimesPage { /* parse list */ }

    // ── Catalogue: search ───────────────────────────────────────────────
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request { /* */ }
    override fun searchAnimeParse(response: Response): AnimesPage { /* */ }
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()  // add filters if site supports

    // ── Catalogue: latest ───────────────────────────────────────────────
    override fun latestUpdatesRequest(page: Int): Request { /* */ }
    override fun latestUpdatesParse(response: Response): AnimesPage { /* */ }
    // If supportsLatest = false: throw UnsupportedOperationException() in both.

    // ── Details + episodes ──────────────────────────────────────────────
    override fun animeDetailsParse(response: Response): SAnime { /* */ }
    override fun episodeListParse(response: Response): List<SEpisode> {
        // ★ Project rule §8: put sub/dub availability in SEpisode.scanlator (below episode name),
        //   NOT in episode.name. Keep episode.name clean (number + title).
        // e.g. episode.scanlator = "SUB • DUB"; episode.name = "Episode 12"
    }

    // ── ext-lib 16: seasons (throw if site has no seasons concept) ─────
    override fun seasonListParse(response: Response): List<SAnime> {
        throw UnsupportedOperationException("Site has no seasons")
    }
    // Only set SAnime.fetch_type = FetchType.Seasons if you actually implement seasons.

    // ── ext-lib 16: hoster pipeline (THE video flow) ───────────────────
    override fun hosterListParse(response: Response): List<Hoster> {
        // Parse the episode's watch page → list of hosters (servers/embeds).
        // Each Hoster: hosterName = "Vidmoly", hosterUrl = "https://embed.../x",
        //              videoList = null (app will call getVideoList(hoster)),
        //              lazy = true if fetching is expensive.
        // First hoster = the one you want auto-selected (after sortHosters).
    }

    override fun videoListParse(response: Response, hoster: Hoster): List<Video> {
        // Fetch videos from hoster.hosterUrl's response.
        // ★ Project rule §7: dedup across SUB/HSUB/DUB (site shares tokens).
        // ★ Label each Video.videoTitle with its audio type: "DUB - 1080p", "SUB - 720p".
        // ★ Set Video.preferred = true on exactly ONE (the best / user-preferred).
        // ★ Set Video.headers with Referer if the host requires it.
    }

    // ── ext-lib 16: lazy per-video resolution (override ONLY if needed) ─
    override suspend fun resolveVideo(video: Video): Video? {
        // Default (inherited) = return video (no-op). Override ONLY if videoUrl needs a per-play fetch.
        // Pattern: return Video(videoUrl = "", internalData = "<token>", initialized = false) from
        // videoListParse, then here use video.internalData to fetch the real URL:
        //   val realUrl = client.newCall(...).awaitSuccess().let { parseRealUrl(it) }
        //   return video.copy(videoUrl = realUrl, initialized = true)
        // Return null on failure — app auto-falls-back to next best video.
        return super.resolveVideo(video)
    }

    // ── ext-lib 16: sorting (override for user preference) ─────────────
    override fun List<Hoster>.sortHosters(): List<Hoster> {
        // Read server preference from `preferences`; reorder. Default = identity.
        val pref = preferences.getString("preferred_server", "") ?: ""
        return sortedByDescending { it.hosterName.contains(pref, true) }
    }

    override fun List<Video>.sortVideos(): List<Video> {
        // ★ Use it.videoTitle (NOT it.quality — that field doesn't exist on v16; the ext-lib KDoc
        //   example is stale).
        val quality = preferences.getString("preferred_quality", "1080") ?: "1080"
        val audio = preferences.getString("preferred_audio", "SUB") ?: "SUB"
        return sortedWith(
            compareBy(
                { it.videoTitle.contains(quality, true) },
                { it.videoTitle.contains(audio, true) },
            ),
        ).reversed()
    }

    // ── Preferences (ConfigurableAnimeSource) ──────────────────────────
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        // Use keiyoushi.utils PreferenceScreen helpers:
        // screen.addListPreference("preferred_quality", entries, values, default, title)
        // screen.addListPreference("preferred_audio", listOf("SUB","HSUB","DUB"), listOf("SUB","HSUB","DUB"), "SUB", "Audio")
        // screen.addListPreference("preferred_server", ..., "Preferred server")
    }
}
```

---

## 4. What you MUST implement (compile errors if missing)

From `AnimeHttpSource` v16 (see `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` §4.2):

- [ ] `popularAnimeRequest`, `popularAnimeParse`
- [ ] `searchAnimeRequest`, `searchAnimeParse`
- [ ] `latestUpdatesRequest`, `latestUpdatesParse` (throw `UnsupportedOperationException` if `supportsLatest = false`)
- [ ] `animeDetailsParse`
- [ ] `episodeListParse`
- [ ] `seasonListParse` (throw `UnsupportedOperationException` if no seasons)
- [ ] `hosterListParse` ★ ext-lib 16
- [ ] `videoListParse(response, hoster)` ★ ext-lib 16 (override — it's `open` stub)
- [ ] `getFilterList` (return empty `AnimeFilterList()` if no filters)

Optional (have defaults):
- [ ] `resolveVideo` (override only for lazy URL)
- [ ] `List<Hoster>.sortHosters()` (override for user pref)
- [ ] `List<Video>.sortVideos()` (override for quality/audio pref)
- [ ] `setupPreferenceScreen` (if `ConfigurableAnimeSource`)
- [ ] `client`, `headersBuilder()` (override for custom OkHttp/headers)

---

## 5. Filling `SAnime` and `SEpisode` correctly

### `SAnime` (from `animeDetailsParse`)
```kotlin
val anime = SAnime.create()
anime.url = "/anime/naruto"                    // path-only (survives domain changes)
anime.title = "Naruto"
anime.description = "..."
anime.genre = "Action, Adventure, Shounen"     // comma-separated
anime.status = SAnime.ONGOING                  // 0=UNKNOWN,1=ONGOING,2=COMPLETED,3=LICENSED,4=PUBLISHING_FINISHED,5=CANCELLED,6=ON_HIATUS
anime.thumbnail_url = "https://.../cover.jpg"
anime.update_strategy = AnimeUpdateStrategy.ALWAYS_UPDATE   // or ONLY_UPDATE_ONCE if complete
anime.fetch_type = FetchType.Episodes          // default; use Seasons only if implementing seasons
anime.initialized = true
```

### `SEpisode` (from `episodeListParse`)
```kotlin
val ep = SEpisode.create()
ep.url = "/watch/naruto-ep-1"                  // path-only
ep.name = "Episode 1"                          // ★ CLEAN: number + title only (rule §8)
ep.episode_number = 1f
ep.date_upload = 1719052800000L                // epoch millis
ep.scanlator = "SUB • DUB"                     // ★ sub/dub availability here (rule §8), below name
// ep.preview_url = "..." (optional)
```

> **Rule §8 reminder:** sub/dub availability goes in `scanlator` (renders below the episode name in
> the UI). Do NOT cram it into `name`. `name` stays clean.

---

## 6. Audio types & dedup (rule §7)

The site has 3 audio types — **SUB** (subbed), **HSUB** (hardsub), **DUB** (dubbed) — NOT 2. Verify
which `data-type`/attribute serves which audio (don't assume; check the live site via agent-browser).

- **Labeling**: each `Video.videoTitle` must clearly state its audio type, e.g. `"SUB - 1080p -
  Vidmoly"`, `"DUB - 1080p - Vidmoly"`.
- **Dedup**: the site shares tokens across audio types (same video served under different labels).
  In `videoListParse`, if two entries resolve to the SAME `videoUrl`, keep ONE and label its audio
  type. Don't return 3 identical URLs.
- **Episode-level availability**: aggregate per-episode sub/hsub/dub availability into
  `SEpisode.scanlator` (e.g. `"SUB • HSUB • DUB"` if all three exist for that episode).

---

## 7. Logging (rule §6 — to be implemented)

Per project rule §6, extensions must log to `Download/1118000/` (NOT logcat), with session-based
files, device info, extension info, date, and enough detail to pinpoint issues. This is a
**future** implementation item — when we build the first extension, we'll add a `lib/filelogger`
module (or put it in `core/`) that writes structured logs to that folder. For now, just know that
every `hosterListParse`/`videoListParse`/`resolveVideo` should emit a log entry on entry, exit,
success, and failure.

---

## 8. (Optional) Deep-link `*UrlActivity` for "Share → Aniyomi"

If you want users to share a site URL and have Aniyomi open the right anime:

1. Implement `ResolvableAnimeSource` on your source:
   ```kotlin
   class SiteName : AnimeHttpSource(), ConfigurableAnimeSource, ResolvableAnimeSource {
       override fun getUriType(uri: String): UriType { /* return Anime/Episode/Unknown based on URI shape */ }
       override suspend fun getAnime(uri: String): SAnime? { /* resolve URI → SAnime */ }
       override suspend fun getEpisode(uri: String): SEpisode? { /* resolve URI → SEpisode */ }
   }
   ```
2. Add a local `src/<lang>/<name>/AndroidManifest.xml` (merged on top of shared):
   ```xml
   <manifest xmlns:android="http://schemas.android.com/apk/res/android">
       <application android:icon="@mipmap/ic_launcher">
           <activity
               android:name=".<lang>.<name>.SiteNameUrlActivity"
               android:exported="true"
               android:excludeFromRecents="true"
               android:theme="@android:style/Theme.NoDisplay">
               <intent-filter android:autoVerify="true">
                   <action android:name="android.intent.action.VIEW" />
                   <category android:name="android.intent.category.DEFAULT" />
                   <category android:name="android.intent.category.BROWSABLE" />
                   <data android:host="sitename.tld" android:scheme="https" />
               </intent-filter>
           </activity>
       </application>
   </manifest>
   ```
3. Write `SiteNameUrlActivity.kt` that re-sends `intent.data` as an
   `eu.kanade.tachiyomi.ANIMESEARCH` intent (see yuzono `src/en/miruro/MiruroUrlActivity.kt` for the
   pattern).

---

## 9. Build & test

```bash
cd /home/z/my-project/EXTENSIONS/<name>/DEV
./gradlew :src:en:<name>:assembleDebug
# APK: src/en/<name>/build/outputs/apk/debug/aniyomi-en.<name>-v16.1.apk
cp src/en/<name>/build/outputs/apk/debug/*.apk ../APK/   # for the user to install (rule §9)
```

Install:
```bash
adb install -r APK/aniyomi-en.<name>-v16.1.apk
```
Enable "Untrusted extensions" in Aniyomi settings (our debug key is untrusted — verified in
`AnimeExtensionLoader.kt:263-274`). Then Extensions → enable → test the golden path:
search → anime → episodes → hoster list → pick hoster → pick video → plays.

---

## 10. Common pitfalls (verified gotchas)

- ❌ `Video(url, quality, videoUrl, …)` legacy ctor — `@Deprecated(level=ERROR)` on v16, won't compile. Use named args.
- ❌ `it.quality.contains(...)` in `sortVideos()` — `quality` doesn't exist on v16. Use `it.videoTitle`.
- ❌ `hoster.copy(...)` — `Hoster` isn't a `data class` on v16, no `copy()`. Construct a new `Hoster(...)`.
- ❌ `hoster.status = ...` / `video.status = ...` — no `status` field on v16 published. (And the app ignores it anyway.)
- ❌ Extending `ParsedAnimeHttpSource` expecting `hosterListSelector`/`videoListSelector` — absent on v16 published. Use `AnimeHttpSource` + Jsoup directly.
- ❌ `versionName = "14.x"` — app will LOAD it (12 ≤ 14 ≤ 16) but won't use the Hoster flow. MUST be `"16.x"`.
- ❌ `versionName = "17.x"` — REJECTED by loader (`> LIB_VERSION_MAX = 16`).
- ❌ Forgetting `Referer` header on `Video.headers` for hosts that require it → mpv 403s.
- ❌ Returning 3 identical `videoUrl`s for SUB/HSUB/DUB → dedup first (rule §7).
- ❌ Putting "SUB/DUB" in `SEpisode.name` → use `scanlator` instead (rule §8).

---

## 11. Related docs

- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` — full API reference (what every method does).
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md` — how the app consumes your Videos.
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` — the build system this extension lives in.
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` — what you can/can't use at compile time.
