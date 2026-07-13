# Guide: Build Setup for ext-lib 16

> Last updated: 2026-06-22 · Status: VERIFIED (build config verified against source; the actual
> first build is a pending verification item — see §6).
>
> This is the canonical build setup for our own ext-lib 16 extensions. It adapts the yuzono
> build-logic (ext-lib 14) to ext-lib 16, with the necessary changes documented inline.

---

## 0. Prerequisites

- **JDK 17+** (NOT Java 11). The published `aniyomiorg/extensions-lib:v16` jar is Java 17 bytecode
  (verified: `.jitpack.yml` `jdk: openjdk17`, `library/build.gradle.kts` `VERSION_17`/`JVM_17`).
  Java 11 `javac` cannot read Java 17 class files. See
  `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` §8.
  - ⚠️ **CURRENT STATUS (2026-06-22):** only the **JRE 21** is installed (no `javac`), and there's
    no passwordless sudo to apt-install the JDK. `openjdk-21-jdk-headless` is in the apt repos.
    **This is a build-time blocker** — the user must provide a JDK (sudo access, self-install, or a
    tarball in `upload/`). JDK 17 or 21 both work. See `MEMORY/guides/03-android-sdk-install.md` §0.1.
- **Android SDK** ✅ **INSTALLED** (2026-06-22, session 05) at `/home/z/my-project/ANDROID_SDK`.
  - `compileSdk = 34`, `platform-tools`, `build-tools;34.0.0` all present.
  - Env: `source /home/z/my-project/.android-env.sh` (sets `ANDROID_HOME`/`ANDROID_SDK_ROOT`/PATH).
  - `local.properties` is OPTIONAL — the build uses `ANDROID_HOME` from the env script when absent
    (AniKoto builds without one). If you want one, place it at `EXTENSIONS/<name>/DEV/local.properties`
    with `sdk.dir=/home/z/my-project/ANDROID_SDK`.
  - Full install procedure: `MEMORY/guides/03-android-sdk-install.md`.
- **Gradle 8.x+** (AGP 9.x requires it; yuzono uses AGP 9.2.1). Comes via the project's `gradlew` wrapper.
- **Git** for versioning.

---

## 1. Repository layout (per-extension Gradle project at `EXTENSIONS/<name>/DEV/`)

Modeled on yuzono, adapted for ext-lib 16. Each extension has its OWN self-contained Gradle project
(the concrete implemented example is AniKoto at `EXTENSIONS/anikoto/DEV/`):

```
EXTENSIONS/<name>/DEV/                 ← one Gradle project per extension
├── settings.gradle.kts              root; includes :stubs + :src:<lang>:<name>
├── build.gradle.kts                 root; minimal (plugin aliases, apply false)
├── gradle.properties                caching/parallel/-Xmx; kotlin.stdlib.default.dependency=false
├── gradle/
│   ├── libs.versions.toml           ★ aniyomi-lib → com.github.aniyomiorg:extensions-lib:v16
│   ├── kei.versions.toml            plugin IDs + SDK versions (★ java = "17")
│   ├── build-logic/                 convention plugins (adapted from yuzono)
│   │   ├── build.gradle.kts
│   │   ├── settings.gradle.kts
│   │   └── src/main/kotlin/
│   │       ├── PluginAndroidBase.kt
│   │       ├── PluginExtensionLegacy.kt   ★ versionName = "16.$versionCode" (was "14.")
│   │       ├── PluginLibrary.kt
│   │       ├── PluginMultiSrc.kt
│   │       ├── PluginSpotless.kt
│   │       └── keiyoushi/gradle/{configurations,extensions,tasks,utils}/
│   └── wrapper/
├── core/                            keiyoushi.utils.* (adapted from yuzono; strip ext-lib-14 legacy overrides)
│   ├── build.gradle.kts
│   └── src/main/kotlin/keiyoushi/utils/
├── common/
│   ├── AndroidManifest.xml          shared manifest (tachiyomi.animeextension uses-feature + meta-data)
│   └── proguard-rules.pro
├── stubs/                           ★ ext-lib v16 stubs (compileOnly — NOT in APK at runtime)
├── lib/                             host extractors + helpers (copy needed ones from yuzono, adapt)
│   └── <name>/
├── lib-multisrc/                    theme templates (copy needed ones from yuzono, adapt)
│   └── <theme>/
├── src/                             the extension itself
│   └── <lang>/<name>/
├── <name>-release.jks               ★ signing keystore (per-extension; in .gitignore)
└── gradlew                          the wrapper script
```

> The Gradle project is relocatable (relative module paths + `rootProject.file()` for the keystore),
> so each extension's `DEV/` is fully self-contained. See `EXTENSIONS/anikoto/DEV/` for the concrete
> implemented example, and `EXTENSIONS/_template/` for the scaffold.

---

## 2. `gradle/libs.versions.toml` (★ = differs from yuzono)

```toml
[versions]
android-gradle = "9.2.1"
coroutines = "1.10.2"
junit = "4.13.2"
kotlin-gradle = "2.3.21"
ktlint = "1.8.0"
spotless = "8.5.0"
tapmoc = "0.4.2"
serialization = "1.7.3"   # PINNED: 1.8+ all-compatibility mode breaks ext compile-only

[libraries]
android-gradle = { module = "com.android.tools.build:gradle", version.ref = "android-gradle" }
junit = { module = "junit:junit", version.ref = "junit" }
kotlin-gradle = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin-gradle" }
ktlint-bom = { module = "com.pinterest.ktlint:ktlint-bom", version.ref = "ktlint" }
spotless-gradle = { module = "com.diffplug.spotless:spotless-plugin-gradle", version.ref = "spotless" }
tapmoc-gradle = { module = "com.gradleup.tapmoc:tapmoc-gradle-plugin", version.ref = "tapmoc" }

coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
injekt-core = { module = "com.github.null2264.injekt:injekt-core", version = "4135455a2a" }
jsoup = { module = "org.jsoup:jsoup", version = "1.22.1" }
kotlin-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlin-protobuf = { module = "org.jetbrains.kotlinx:kotlinx-serialization-protobuf", version.ref = "serialization" }
kotlin-json-okio = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json-okio", version.ref = "serialization" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version = "5.3.2" }
quickjs = { module = "app.cash.quickjs:quickjs-android", version = "0.9.2" }
rxjava = { module = "io.reactivex:rxjava", version = "1.3.8" }

# ★ THE ext-lib 16 DEPENDENCY (was: com.github.komikku-app:aniyomi-extensions-lib:bdc8184127)
aniyomi-lib = { module = "com.github.aniyomiorg:extensions-lib", version = "v16" }

[plugins]
android-application = { id = "com.android.application", version.ref = "android-gradle" }
android-library = { id = "com.android.library", version.ref = "android-gradle" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin-gradle" }
kotlin-samWithReceiver = { id = "org.jetbrains.kotlin.plugin.sam.with.receiver", version.ref = "kotlin-gradle" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin-gradle" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }

[bundles]
common = ["coroutines-core", "coroutines-android", "injekt-core", "rxjava", "kotlin-protobuf", "kotlin-json", "kotlin-json-okio", "jsoup", "okhttp", "aniyomi-lib", "quickjs"]
```

Only TWO changes from yuzono:
1. `aniyomi-lib` module coordinate: `komikku-app:aniyomi-extensions-lib:bdc8184127` → `aniyomiorg:extensions-lib:v16`.
2. (In `kei.versions.toml`) `java = "17"` (was `"11"`).

---

## 3. `gradle/kei.versions.toml` (★ java = 17)

```toml
[versions]
android-sdk-min = "21"
android-sdk-compile = "34"
android-sdk-target = "34"
java = "17"   # ★ was "11" — required to read the Java-17 ext-lib v16 stub jar

[plugins]
android-base = { id = "kei.plugins.android.base" }
extension-legacy = { id = "kei.plugins.extension.legacy" }
library = { id = "kei.plugins.library" }
multisrc = { id = "kei.plugins.multisrc" }
spotless = { id = "kei.plugins.spotless" }
```

---

## 4. `settings.gradle.kts`

Identical to yuzono's (see `MEMORY/research/02-reference-extension-build-and-structure.md` §1):
- `includeBuild("gradle/build-logic")`
- Two version catalogs: `libs` + `kei`
- `repositoriesMode = FAIL_ON_PROJECT_REPOS`; repos: `google()`, `mavenCentral()`, `maven("https://www.jitpack.io")`
- `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`
- `include(":core")`; auto-include `lib/*`, `lib-multisrc/*`, `src/<lang>/<name>`

The JitPack repo is MANDATORY — `com.github.aniyomiorg:extensions-lib:v16` is served from JitPack.

---

## 5. `PluginExtensionLegacy.kt` — the ONE critical change

Copy yuzono's `gradle/build-logic/src/main/kotlin/PluginExtensionLegacy.kt` verbatim, then make
exactly ONE change:

```kotlin
// In defaultConfig:
versionName = "16.$versionCode"   // ★ was "14.$versionCode"
```

Why: `AnimeExtensionLoader` (verified, `SHARED/REFERENCE_HUB/aniyomi-app/.../AnimeExtensionLoader.kt:47-48,
254-260`) parses `libVersion = versionName.substringBeforeLast('.').toDouble()` and rejects
extensions where `libVersion < 12 || libVersion > 16`. So `versionName = "16.<code>"` → `libVersion
= 16.0` → accepted. `"14.<code>"` would also be accepted (12 ≤ 14 ≤ 16) but then the app treats it
as ext-lib 14 and won't use the Hoster flow — so we MUST use `"16."`.

Everything else in `PluginExtensionLegacy.kt` stays the same (namespace, applicationIdSuffix,
manifestPlaceholders, keep-rules, signing, etc.).

---

## 6. Open verification items (confirm at first build)

These are verified against source but NOT yet confirmed by a real build. The first `./gradlew
:src:<lang>:<name>:assembleRelease` will confirm:

1. **JitPack serves `com.github.aniyomiorg:extensions-lib:v16`.** The tag exists on GitHub; JitPack
   should build it on demand. If it fails (JitPack timeout/build error), fallback:
   `includeBuild("SHARED/REFERENCE_HUB/ext-lib-aniyomiorg")` + change the dependency to
   `compileOnly(project(":library"))` of that composite build. (We have the full clone.)
2. **Java 17 toolchain reads the v16 jar.** Should work (17 ≥ 17). If our build runs on JDK 17,
   `kotlinc`/`javac` read class file v61 fine.
3. **`tapmoc` compatibility with Java 17.** yuzono uses `tapmoc.configureJavaCompatibility(11)`.
   For us it'd be `configureJavaCompatibility(17)` (or we drop tapmoc if AGP 9.2.1 + JDK 17 doesn't
   need it). `tapmoc`'s job is AGP-compat for newer AGP on older Java — with Java 17 it may be
   redundant. Verify at first build; remove if unused.
4. **`core/Source.kt` legacy overrides.** yuzono's `core/Source.kt` overrides legacy ext-lib-14
   methods (`videoListParse(response)`, `videoUrlParse`, etc.) to throw
   `UnsupportedOperationException` with `TODO: Remove with ext lib 16`. On v16, those methods
   DON'T EXIST in the interface — so the overrides won't compile. **Delete them** when adapting
   `core/Source.kt`. (Keep `preferences`, `json`, `displayToast`, `migration`.)
5. **R8 keep rules** for the source class still suffice with the new suspend Hoster methods. The
   `generateKeepRules` task writes `-keep class <appId><extClass> { <init>(); }` — only the
   no-arg ctor. The suspend methods are overridden, not called reflectively, so this should be fine.
   Verify no R8 stripping at first test.

---

## 7. Build commands

```bash
source /home/z/my-project/.android-env.sh    # every new shell
cd /home/z/my-project/EXTENSIONS/<name>/DEV

./gradlew :src:en:<name>:assembleDebug          # build one debug APK
./gradlew :src:en:<name>:assembleRelease        # build one release APK (signed with <name>-release.jks)
./gradlew spotlessApply                          # format code (ktlint + google-java-format)
./gradlew spotlessCheck                          # CI-style format check
./gradlew :src:en:<name>:lint                    # run Android lint
```

Output APK location: `src/<lang>/<name>/build/outputs/apk/<debug|release>/aniyomi-<lang>.<name>-v16.<code>.apk`.

> Built APKs do NOT get committed to git — they're rebuildable. Copy to `EXTENSIONS/<name>/APK/`
> for the user to install (project rule §9). The download webpage serves the current extension's
> APK via `/api/apk?type=release|debug` (reads from the build output).

---

## 8. Install & test on device

1. Enable "Untrusted extensions" in Aniyomi settings → Advanced (our debug-signed APK is untrusted;
   verified: `AnimeExtensionLoader.kt:263-274` calls `trustExtension.isTrusted`).
2. Install the APK: `adb install -r aniyomi-en.<name>-v16.<code>.apk` (or tap the APK in a file
   manager). Aniyomi auto-detects the `tachiyomi.animeextension` `<uses-feature>`.
3. In Aniyomi → Extensions → enable the new extension.
4. Test: search → open anime → episode list → tap episode → quality sheet shows hosters → pick →
   video plays.
5. Check `Download/1118000/` for the in-app log (once we implement it — project rule §6).

---

## 9. Related docs

- `MEMORY/ext-lib/01-ext-lib-16-source-and-versioning.md` — why `v16` not the komikku fork.
- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` — what to implement.
- `MEMORY/research/02-reference-extension-build-and-structure.md` — the yuzono build-logic we're adapting.
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` — why Java 17 + which fields are usable.
- `MEMORY/guides/02-how-to-create-a-new-extension.md` — per-extension file layout.
