# Reference Extension Build System & Structure (yuzono/anime-extensions)

> Last updated: 2026-06-22 · Status: VERIFIED
> Source: `SHARED/REFERENCE_HUB/anime-extensions-ref/` (yuzono/anime-extensions, pinned to ext-lib 14).
>
> This documents the **STRUCTURE** (build system, manifest, source class patterns, core utils, lib
> helpers) that we'll adapt for our own ext-lib 16 extensions. The build-logic + module layout is
> reusable; the ext-lib dependency + legacy video methods must change (see
> `MEMORY/ext-lib/01-ext-lib-16-source-and-versioning.md`).

All paths relative to `SHARED/REFERENCE_HUB/anime-extensions-ref/`.

---

## 1. Build system map

```
settings.gradle.kts  ── root project "Yuzono-Anime"
  ├─ includeBuild("gradle/build-logic")        composite build: convention plugins
  ├─ versionCatalog "libs" ← gradle/libs.versions.toml
  ├─ versionCatalog "kei"  ← gradle/kei.versions.toml   (custom plugin IDs + SDK versions)
  ├─ include(":core")                            shared utils (keiyoushi.utils)
  ├─ lib/<name>          → lib:<name>             per-host extractor/helper modules (73)
  ├─ lib-multisrc/<name> → lib-multisrc:<name>    theme templates (10)
  └─ src/<lang>/<name>   → src:<lang>:<name>       individual extensions (auto-loaded)

gradle/build-logic/  ── convention plugins (applied via kei.versions.toml IDs)
  ├─ PluginAndroidBase.kt     shared Android config (SDK 21/34, Java 11 via tapmoc, spotless)
  ├─ PluginExtensionLegacy.kt ★ for src/<lang>/<name>/  (application module, builds APK)
  ├─ PluginLibrary.kt         for lib/<name>/            (library module, no resources)
  ├─ PluginMultiSrc.kt        for lib-multisrc/<name>/   (library module, has resources/manifest)
  └─ PluginSpotless.kt        ktlint + google-java-format + custom RandomUACheck

core/  ── keiyoushi.utils.* (JSON, network, coroutines, preferences, crypto, RSC parser, …)
common/AndroidManifest.xml  ── the shared manifest every extension uses
common/proguard-rules.pro
```

### `settings.gradle.kts` key points
- `rootProject.name = "Yuzono-Anime"`
- `repositoriesMode = FAIL_ON_PROJECT_REPOS` — no per-module `repositories {}` allowed.
- `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` — `project(":core")` etc. typesafe.
- `loadAllIndividualExtensions()` auto-includes every `src/<lang>/<name>/`. For local dev, comment
  it out and use `loadIndividualExtension("en", "tokuzilla")` to build one at a time (faster).

---

## 2. Version catalogs

### `gradle/libs.versions.toml` (the primary catalog)

| Section | Entry | Value | Notes |
|---|---|---|---|
| versions | android-gradle | `9.2.1` | AGP |
| versions | kotlin-gradle | `2.3.21` | Kotlin |
| versions | coroutines | `1.10.2` | |
| versions | serialization | `1.7.3` | **pinned** — 1.8+ "all-compatibility" mode breaks ext compile-only |
| versions | ktlint | `1.8.0` | |
| versions | spotless | `8.5.0` | |
| versions | tapmoc | `0.4.2` | `com.gradleup.tapmoc` — Java-compat shim for newer AGP |
| versions | junit | `4.13.2` | |
| libraries | aniyomi-lib | `com.github.komikku-app:aniyomi-extensions-lib:bdc8184127` | **← ext-lib 14 pin; WE change to `com.github.aniyomiorg:extensions-lib:v16`** |
| libraries | okhttp | `com.squareup.okhttp3:okhttp:5.3.2` | |
| libraries | jsoup | `org.jsoup:jsoup:1.22.1` | |
| libraries | injekt-core | `com.github.null2264.injekt:injekt-core:4135455a2a` | DI (commit-pinned) |
| libraries | quickjs | `app.cash.quickjs:quickjs-android:0.9.2` | JS eval for obfuscated sites |
| libraries | rxjava | `io.reactivex:rxjava:1.3.8` | legacy (ext-lib still references Rx in deprecated methods) |
| libraries | kotlin-json / -protobuf / -json-okio | (serialization 1.7.3) | |
| libraries | coroutines-core / -android | (1.10.2) | |
| bundles | common | `[coroutines-core, coroutines-android, injekt-core, rxjava, kotlin-protobuf, kotlin-json, kotlin-json-okio, jsoup, okhttp, aniyomi-lib, quickjs]` | the **compileOnly** bundle every module gets |
| plugins | android-application, android-library, kotlin-jvm, kotlin-samWithReceiver, kotlin-serialization, spotless | | |

### `gradle/kei.versions.toml` (custom plugin IDs + SDK)

```toml
[versions]
android-sdk-min = "21"
android-sdk-compile = "34"
android-sdk-target = "34"
java = "11"

[plugins]
android-base = { id = "kei.plugins.android.base" }
extension-legacy = { id = "kei.plugins.extension.legacy" }
library = { id = "kei.plugins.library" }
multisrc = { id = "kei.plugins.multisrc" }
spotless = { id = "kei.plugins.spotless" }
```

> ⚠️ Note the ext-lib's OWN `build.gradle.kts` uses Java 17 / compileSdk 36, but the yuzono
> build-logic uses Java 11 / compileSdk 34 via `tapmoc`. When we set up our build, we must verify
> the published v16 jar's bytecode target is ≤ 11 (otherwise tapmoc won't save us). This is an open
> item for the extensions setup.

---

## 3. Convention plugins (`gradle/build-logic/src/main/kotlin/`)

### `PluginAndroidBase.kt` — applied by all three module plugins
- `configureKotlin()`: Java 11 compat via **tapmoc**, `optIn = ExperimentalSerializationApi`,
  `-Xcontext-parameters`.
- `android { compileSdk = 34; defaultConfig { minSdk = 21; (targetSdk = 34 if application) } }`.
- Attaches `proguard-rules.pro` if present.
- `preBuild.dependsOn(spotlessTaskName())` — `spotlessApply` locally, `spotlessCheck` on CI
  (env `CI=true`).

### `PluginExtensionLegacy.kt`  ★ THE per-extension plugin (builds an APK)
Key behavior:
- Applies `com.android.application` + `kotlin.serialization` + `android.base` + `spotless`.
- **Assertions** (hard errors at config time):
  - `!extra.has("pkgNameSuffix")` — old Tachiyomi field forbidden.
  - `!extra.has("libVersion")` — old Tachiyomi field forbidden.
  - `extName` must be ASCII (romanized).
  - `extClass` MUST start with a dot (e.g. `.Tokuzilla`).
- Reads `themePkg` from `ext { }`. If set, `evaluationDependsOn(":lib-multisrc:$themePkg")`.
- `android.namespace = "eu.kanade.tachiyomi.animeextension"` (same for ALL extensions).
- **Source sets** (this is unusual):
  - `manifest.srcFile = rootProject.file("common/AndroidManifest.xml")` (shared manifest).
  - `java`/`kotlin` src dirs → `"src"` (NOT `"src/main/kotlin"` — so files live at `src/eu/kanade/…`).
  - `res` → `"res"`, `assets` → `"assets"`.
- `defaultConfig`:
  - `applicationIdSuffix = "<lang>.<name>"` → full applicationId becomes
    `eu.kanade.tachiyomi.animeextension.<lang>.<name>`.
  - `versionCode = theme.baseVersionCode + overrideVersionCode` (multisrc) OR `extVersionCode` (standalone).
  - `versionName = "14.$versionCode"` ← **the ext-lib 14 marker; change to `"16.$versionCode"` for us.**
  - `archivesName = "aniyomi-$applicationIdSuffix-v$versionName"`.
  - `manifestPlaceholders`: `appName = "Aniyomi: $extName"`, `extClass = $extClass`,
    `nsfw = isNsfw ? 1 : 0`. If multisrc + baseUrl set: also `SOURCEHOST` + `SOURCESCHEME`.
- `signingConfigs.release`: reads `KEY_STORE_PASSWORD`/`ALIAS`/`KEY_PASSWORD` env vars from
  `signingkey.jks` at repo root; falls back to debug signing if absent.
- `buildTypes.release`: `isMinifyEnabled = true`, proguard, `vcsInfo.include = false`.
- `buildFeatures.buildConfig = true`.
- Injects `BuildConfig` string fields: `MEGACLOUD_API`, `KISSKH_API`, `KISSKH_SUB_API`, `KAISVA`,
  `TMDB_API = System.getenv("TMDB_API")`.
- `androidComponents.onVariants`:
  - Registers `generate<Variant>KeepRules` task writing `-keep class <appId><extClass> { <init>(); }`
    so R8 doesn't strip the source-class constructor (app instantiates it reflectively).
  - Merges an optional local `AndroidManifest.xml` from the extension's own dir (for deep-link
    `*UrlActivity`).
- `dependencies`:
  - If multisrc: `implementation(theme)`.
  - `implementation(project(":core"))` — always.
  - `compileOnly(libs.bundles.common)` — aniyomi-lib + okhttp + jsoup + serialization + injekt + rxjava + quickjs.
    **Compile-only** because the app ships these at runtime; bundling would duplicate classes.
- `afterEvaluate`: strips `createdBy` + empties `appMetadata` from the APK (anti-fingerprinting).

### `PluginLibrary.kt` — for `lib/<name>/`
- `com.android.library` + `kotlin.serialization` + `android.base` + `spotless`.
- `namespace = "aniyomi.lib.${project.name}"`.
- `androidResources.enable = false` (libs have no resources).
- src dirs → `"src"`, assets → `"assets"`.
- `compileOnly(libs.bundles.common)` + `implementation(project(":core"))`.

### `PluginMultiSrc.kt` — for `lib-multisrc/<name>/`
- `com.android.library` + `kotlin.serialization` + `android.base` + `spotless`.
- `namespace = "eu.kanade.tachiyomi.multisrc.${project.name}"`.
- `manifest.srcFile = "AndroidManifest.xml"` (themes ship their own for `*UrlActivity`).
- src → `"src"`, res → `"res"`, assets → `"assets"`.
- `compileOnly(libs.bundles.common)` + `implementation(project(":core"))`.

### `PluginSpotless.kt`
- ktlint (1.8.0) on `src/**/*.kt` + `*.kts`; `max_line_length = 2147483647` (no line-length check;
  the real 140-char limit is in `.editorconfig`).
- google-java-format on `src/**/*.java`.
- trim-trailing-whitespace + end-with-newline on `*.gradle` + `src/**/*.xml`.
- **Custom `RandomUACheck`**: throws if a file references `keiyoushi.lib.randomua` but doesn't
  override `getMangaUrl(` — enforces stable URLs for tracker matching when using random UAs.

---

## 4. The `core/` module — `keiyoushi.utils.*`

`core/build.gradle.kts`: `com.android.library` + namespace `keiyoushi.core`. Every module gets
`implementation(project(":core"))` automatically from its plugin.

`core/src/main/kotlin/keiyoushi/utils/`:

| File | Key API |
|---|---|
| `Json.kt` | `jsonInstance`, `String.parseAs<T>()`, `Response.parseAs<T>()`, `T.toJsonString()`, `String.toJsonBody()`, `T.toJsonRequestBody()` |
| `Protobuf.kt` | `protoInstance`, `ByteArray.decodeProto<T>()`, `Response.parseAsProto<T>()`, `T.encodeProtoBase64()` |
| `GraphQL.kt` | `graphQLBody`, `graphQLPost`, `graphQLGet`, `persistedQueryExtension`, `Response.parseGraphQLAs<T>()`, `GraphQLErrorInterceptor` |
| `Network.kt` | `OkHttpClient.get(url, headers, cache)`, `.post(...)` suspend helpers, `Response.useAsJsoup()`, `Response.bodyString()` |
| `Coroutines.kt` | `parallelMap`, `parallelMapNotNull`, `parallelFlatMap`, `parallelCatchingFlatMap`, `parallelCatchingMapNotNull`, `*Blocking` variants |
| `Preferences.kt` | `AnimeHttpSource.getPreferences(migration)`, `getPreferencesLazy(migration)`, `LazyMutable<T>`, `PreferenceDelegate<T>`, `SharedPreferences.delegate(key, default)`, `PreferenceScreen.get/addEditTextPreference`, `get/addListPreference`, `get/addSetPreference`, `get/addSwitchPreference` |
| `Source.kt` | `abstract class Source : AnimeHttpSource(), ConfigurableAnimeSource` — base class with `preferences`, `json`, `displayToast`, `migration`. **Legacy ext-lib-14 `*Request`/`*Parse` methods overridden to throw `UnsupportedOperationException`** with `TODO: Remove with ext lib 16` comments. (We delete these on v16.) |
| `UrlUtils.kt` | `fixUrl(url)`, `fixUrl(url, baseUrl)` — resolves `//`, `/path`, relative against base |
| `Crypto.kt` | `decodeHex()`, `decodeHexToString()`, `toHex()`, `rc4(key, data, skip)` |
| `Date.kt` | `SimpleDateFormat.tryParse(date: String?): Long` |
| `Context.kt` | `applicationContext: Application` (from Injekt) |
| `NextJs.kt` + `reactFlight/*` | `Document.extractNextJs<T>()`, `String.extractNextJsRsc<T>()`, `Response.extractNextJs<T>()` — React Flight (RSC) + Next.js hydration parser |

> The `Source.kt` base class is the yuzono pattern for a "clean" ext-lib source. On ext-lib 16, we
> adapt it: remove the legacy `*Request`/`*Parse` `UnsupportedOperationException` overrides (those
> methods don't exist in v16), keep the `preferences`/`json`/`displayToast`/`migration` helpers.

---

## 5. The shared manifest — `common/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="tachiyomi.animeextension" />
    <application android:icon="@mipmap/ic_launcher" android:allowBackup="false" android:label="${appName}">
        <meta-data android:name="tachiyomi.animeextension.class" android:value="${extClass}" />
        <meta-data android:name="tachiyomi.animeextension.nsfw" android:value="${nsfw}" />
    </application>
</manifest>
```

The placeholders `${appName}`, `${extClass}`, `${nsfw}` are filled by `PluginExtensionLegacy`'s
`manifestPlaceholders`. **The Aniyomi app discovers extensions by scanning installed APKs for the
`tachiyomi.animeextension` `<uses-feature>` and reads `tachiyomi.animeextension.class` meta-data to
know which class to reflectively instantiate.** That class must have a no-arg constructor.

Extensions can ADD a local `AndroidManifest.xml` (merged on top) for deep-link `*UrlActivity`.

---

## 6. Concrete extension anatomy — `src/en/tokuzilla/`

A clean single-file anime-streaming extension. **Directory layout:**
```
src/en/tokuzilla/
├── build.gradle                              (Groovy, not .kts)
├── res/mipmap-{h,mdpi,xhpi,xxhdpi,xxxhdpi}/ic_launcher.png
└── src/eu/kanade/tachiyomi/animeextension/en/tokuzilla/Tokuzilla.kt
```
(No `AndroidManifest.xml` — uses the shared one. No `res/values/strings.xml`. No `assets/`.)

### `src/en/tokuzilla/build.gradle` (full)
```groovy
ext {
    extName = 'Tokuzilla'
    extClass = '.Tokuzilla'
    extVersionCode = 29
}
apply plugin: "kei.plugins.extension.legacy"
dependencies {
    implementation(project(':lib:chillxextractor'))
}
```

### `Tokuzilla.kt` structure (196 lines)
- `package eu.kanade.tachiyomi.animeextension.en.tokuzilla` ← **convention: `eu.kanade.tachiyomi.animeextension.<lang>.<name>`**
- `class Tokuzilla : ParsedAnimeHttpSource(), ConfigurableAnimeSource`
  - (on ext-lib 16 we'd use `AnimeHttpSource()` directly, not `ParsedAnimeHttpSource()`)
- Overrides: `name`, `baseUrl`, `lang`, `supportsLatest = false`.
- `private val preferences by getPreferencesLazy()` (from `keiyoushi.utils`).
- Popular/Search/Details/Episodes via Jsoup selectors.
- Videos via `ChillxExtractor(client, headers).videoFromUrl(frameLink, baseUrl)` from `:lib:chillxextractor`.
- `override fun List<Video>.sort(): List<Video>` — quality preference (← ext-lib 14 name; v16 is `sortVideos()`).
- `override fun setupPreferenceScreen(screen: PreferenceScreen)` — `ListPreference` for preferred quality.
- Throws `UnsupportedOperationException()` for unused `latestUpdates*`/`videoListSelector`/`videoFromElement`/`videoUrlParse`/`episodeFromElement`.

### Second example — `src/en/miruro/` (deep-link URL activity)
`build.gradle`:
```groovy
ext {
    extName = 'Miruro.tv'
    extClass = '.Miruro'
    isNsfw = true
    extVersionCode = 3
}
apply plugin: "kei.plugins.extension.legacy"
```
Local `AndroidManifest.xml` (merged on top of shared) adds a `MiruroUrlActivity` with
`<intent-filter>` for `miruro.tv` / `miruro.to` / `miruro.bz` / `miruro.ru` `VIEW` actions. The
activity re-sends as an `eu.kanade.tachiyomi.ANIMESEARCH` intent with `query = url` +
`filter = packageName` so the app searches within this extension. `android:name=".en.miruro.MiruroUrlActivity"`
is relative to the namespace `eu.kanade.tachiyomi.animeextension`.

---

## 7. Multisrc theme anatomy — `lib-multisrc/zorotheme/`

### `lib-multisrc/zorotheme/build.gradle.kts` (full)
```kotlin
import keiyoushi.gradle.extensions.baseVersionCode
plugins { alias(kei.plugins.multisrc) }
baseVersionCode = 8
```

### Directory structure
```
lib-multisrc/zorotheme/
├── build.gradle.kts
└── src/eu/kanade/tachiyomi/multisrc/zorotheme/
    ├── ZoroTheme.kt          (abstract class, ~436 lines)
    ├── ZoroThemeFilters.kt
    └── dto/ZoroThemeDto.kt
```

### Consumer — `src/en/zoro/build.gradle`
```groovy
ext {
    extName = 'Zoro'
    extClass = '.Zoro'
    themePkg = 'zorotheme'
    overrideVersionCode = 44
    baseUrl = 'https://zoro.to'
    isNsfw = false
}
apply plugin: "kei.plugins.extension.legacy"
dependencies {
    implementation(project(':lib:cryptoaes'))
    implementation(project(':lib:playlistutils'))
    // ...
}
```
`themePkg` triggers `evaluationDependsOn(":lib-multisrc:zorotheme")` in `PluginExtensionLegacy`; the
extension's `versionCode = theme.baseVersionCode (8) + overrideVersionCode (44) = 52`. The
extension's source class extends `ZoroTheme` (the abstract class from the multisrc lib) and only
overrides site-specific bits (baseUrl, maybe selectors).

---

## 8. The `lib/` helper modules (73 modules)

Each is a `com.android.library` module (`PluginLibrary`), namespace `aniyomi.lib.<name>`,
`compileOnly(libs.bundles.common)` + `implementation(project(":core"))`, no resources.

### One-line inventory (folder name → purpose, inferred + verified for the important ones)

**Extractors (host-specific video resolvers):**
`amazonextractor`, `bloggerextractor`, `burstcloudextractor`, `buzzheavierextractor`, `cdaextractor`,
`chillxextractor`, `dailymotionextractor`, `doodextractor`, `dopeflixextractor`, `fastreamextractor`,
`filemoonextractor`, `fireplayerextractor`, `fusevideoextractor`, `gdriveplayerextractor`,
`gogostreamextractor`, `goodstreamextractor`, `googledriveepisodes`, `googledriveextractor`,
`googledriveplayerextractor`, `luluextractor`, `lycorisextractor`, `megaupextractor`,
`megamaxmultiserver`, `megacloudextractor`, `mixdropextractor`, `mp4uploadextractor`, `okruextractor`,
`pixeldrainextractor`, `rapidcloudextractor`, `rapidshareextractor`, `rumbleextractor`,
`savefileextractor`, `sendvidextractor`, `sibnetextractor`, `streamdavextractor`, `streamhubextractor`,
`streamlareextractor`, `streamplayextractor`, `streamsilkextractor`, `streamtapeextractor`,
`streamupextractor`, `streamwishextractor`, `upstreamextractor`, `uqloadextractor`, `vidbomextractor`,
`vidguardextractor`, `vidhideextractor`, `vidlandextractor`, `vidmolyextractor`, `vidoextractor`,
`vidsrcextractor`, `vkextractor`, `voeextractor`, `vudeoextractor`, `youruploadextractor`.

**Utility / helper modules:**
- `playlistutils` — **m3u8/playlist parsing & manipulation** (master playlist → variants, header
  injection, subtitle extraction). One of the most-used helpers for HLS sites.
- `m3u8server` — spins up a local HTTP server to proxy/rewrite m3u8 requests (for sites that need
  header injection on segment requests).
- `cryptoaes` — **AES crypto helpers** (decrypt encrypted server configs / tokens). Common on sites
  that encrypt their server-list JSON.
- `lzstring` — LZ-String decompression (some sites compress their JS/JSON).
- `unpacker` — JS unpacker (de-obfuscate `eval(p,a,c,k,e,d)` style packed JS).
- `cloudflareinterceptor` — Cloudflare challenge / clearance interceptor.
- `cookieinterceptor` — cookie capture/replay interceptor.
- `randomua` — random User-Agent generation (paired with the Spotless `RandomUACheck` that requires
  `getMangaUrl` override).
- `textinterceptor` — interceptor for returning text bodies.
- `synchrony` — (de-obfuscation helper).
- `seedrandom` — seeded PRNG (for reproducing site-side random token generation).
- `i18n` — shared internationalization strings.
- `javcoverfetcher` — (JAV cover fetching; not relevant for anime).
- `universalextractor` — **generic video extractor** that tries many common embed patterns. Useful
  as a fallback when a site uses an unknown player.
- `dataimage` — parse `data:` URIs.
- `zipinterceptor` — interceptor for zip-packed responses.

### `lib/<name>/build.gradle.kts` common pattern
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(kei.plugins.android.base)        // OR alias(kei.plugins.library)
    alias(kei.plugins.spotless)
}
android { namespace = "aniyomi.lib.<name>" }
// src dir = "src"; no resources
dependencies {
    compileOnly(libs.bundles.common)
    implementation(project(":core"))
    // sometimes implementation(project(":lib:other")) for cross-lib deps
}
```

---

## 9. Versioning scheme

- `extVersionCode` (per extension) — monotonically increasing integer, bumped on each release.
- `overrideVersionCode` (multisrc consumers) — added to `theme.baseVersionCode`.
- `baseVersionCode` (multisrc themes) — bumped when the theme itself changes (all consumers get a
  version bump).
- `versionCode = extVersionCode` (standalone) OR `theme.baseVersionCode + overrideVersionCode` (multisrc).
- `versionName = "<extlibmajor>.$versionCode"` — currently `"14.$versionCode"` (ext-lib 14). **We
  change the build-logic to `"16.$versionCode"` for ext-lib 16.**
- `applicationId = eu.kanade.tachiyomi.animeextension.<lang>.<name>`.
- `archivesName = aniyomi-<lang>.<name>-v<versionName>`.
- CI (`.github/workflows/build_push.yml`) builds each `src/<lang>/<name>` module into an APK and
  publishes with these coordinates. (We don't use their CI; we build locally.)

The `extLibVersion` the app checks against an installed extension is derived from `versionName`'s
prefix. So `"14.x"` ⇒ ext-lib 14, `"16.x"` ⇒ ext-lib 16. The app refuses to load an extension whose
ext-lib major doesn't match its own current major (this is the `AnimeExtensionLoader` version-compat
check — we should verify the exact rule when setting up the build).

---

## 10. "To create a new extension" checklist (derived from tokuzilla)

Minimal standalone extension needs:
1. `src/<lang>/<name>/build.gradle` with `ext { extName; extClass; extVersionCode }` +
   `apply plugin: "kei.plugins.extension.legacy"` + any `implementation(project(":lib:…"))`.
2. `src/<lang>/<name>/res/mipmap-*/ic_launcher.png` (icon; or inherit core's default).
3. `src/<lang>/<name>/src/eu/kanade/tachiyomi/animeextension/<lang>/<name>/<ClassName>.kt`:
   - `package eu.kanade.tachiyomi.animeextension.<lang>.<name>`
   - `class <ClassName> : AnimeHttpSource(), ConfigurableAnimeSource` (ext-lib 16 — NOT
     `ParsedAnimeHttpSource`)
   - override `name`, `baseUrl`, `lang`, `supportsLatest`
   - implement all abstract `*Request`/`*Parse` methods (see
     `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` §14 checklist)
4. (optional) `src/<lang>/<name>/AndroidManifest.xml` for a deep-link `*UrlActivity`.
5. (optional) `src/<lang>/<name>/assets/` for bundled assets.
6. (optional) `src/<lang>/<name>/proguard-rules.pro`.

No `AndroidManifest.xml` is required (shared one is used). No `strings.xml` required. No
`src/main/` directory — Kotlin goes directly under `src/`.

---

## 11. Things to verify later / open questions

- **`tapmoc` (`com.gradleup.tapmoc:0.4.2`)**: needs deeper understanding. It's a "Test AGP Plugin
  Mock" / compat shim that lets extensions compile against AGP 9.x / Java 17+ while targeting
  Android 21 runtime. We must confirm it's still needed for ext-lib 16 and whether the published
  v16 jar's bytecode target is compatible.
- **`AnimeExtensionLoader` version-compat**: the exact rule the app uses to accept/reject an
  installed extension by ext-lib major. Need to read `app/.../extension/anime/util/AnimeExtensionLoader.kt`.
- **`signingkey.jks`**: for release-signed APKs. We'll likely build debug-signed APKs for testing
  (the app installs debug-signed extension APKs fine).
- **R8 keep rules**: the `generateKeepRules` task writes `-keep class <appId><extClass> { <init>(); }`.
  Confirm this is sufficient for ext-lib 16 source classes (which now have more suspend methods).
