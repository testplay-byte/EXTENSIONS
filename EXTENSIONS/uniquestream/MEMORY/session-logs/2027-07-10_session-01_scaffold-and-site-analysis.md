# Session 01 — Scaffold Gradle Project for UniQuestream Extension

**Date:** 2027-07-10  
**Agent:** scaffold-agent  
**Task ID:** 9

## Objective

Scaffold a complete Gradle project for the "UniQuestream" Aniyomi extension by copying and adapting the build system from the AniKoto extension.

## What Was Done

### 1. Read AniKoto Reference Structure
- Analyzed `/EXTENSIONS/anikoto/DEV/` directory layout
- Read all build files: `settings.gradle.kts`, `build.gradle.kts` (root), `stubs/build.gradle.kts`, `src/en/anikoto/build.gradle.kts`
- Read `gradle/libs.versions.toml`, `gradle.properties`, `gradle-wrapper.properties`
- Identified 27 stub Kotlin files in `stubs/src/main/kotlin/`
- Noted AniKoto's `extClass` pattern (full path, no leading dot)

### 2. Created Directory Structure

```
DEV/
├── settings.gradle.kts
├── build.gradle.kts (root)
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   ├── kei.versions.toml
│   └── wrapper/gradle-wrapper.{properties,jar}
├── common/
│   ├── AndroidManifest.xml
│   └── proguard-rules.pro
├── stubs/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── eu/kanade/tachiyomi/... (27 files from AniKoto)
│       ├── eu/kanade/tachiyomi/network/PreferenceStore.kt (NEW)
│       └── keiyoushi/utils/PreferencesExtensions.kt (NEW)
├── src/en/uniquestream/
│   ├── build.gradle.kts
│   ├── res/mipmap-*/ic_launcher.png (5 placeholder icons)
│   └── src/eu/kanade/tachiyomi/animeextension/en/uniquestream/UniQuestream.kt
└── uniquestream-release.jks (placeholder keystore)
```

### 3. Adapted Build Files

| Setting | Value |
|---|---|
| `rootProject.name` | `UniQuestream-Anime` |
| `extName` | `UniQuestream` |
| `extClass` | `eu.kanade.tachiyomi.animeextension.en.uniquestream.UniQuestream` (full path, no leading dot) |
| `applicationIdSuffix` | `en.uniquestream180` → final package `eu.kanade.tachiyomi.animeextension.en.uniquestream180` |
| `extVersionCode` | 1 |
| `versionName` | `16.1` |
| `versionId` | 1 |
| `isNsfw` | false |
| `baseUrl` | `https://anime.uniquestream.net` |
| `archivesName` | `aniyomi-en.uniquestream180-v16.1` |

### 4. Source Sets
- Source module uses `java.srcDirs("src")` — Kotlin files go directly in `src/`, not `src/main/kotlin/`
- This differs from AniKoto which uses the standard AGP layout

### 5. New Stubs Added
- `keiyoushi/utils/PreferencesExtensions.kt` — stub for `getPreferencesLazy()` (needed by the skeleton)
- `eu/kanade/tachiyomi/network/PreferenceStore.kt` — stub for `PreferenceStore` class

### 6. Keystore
- Generated placeholder `uniquestream-release.jks` (RSA 2048-bit, alias `uniquestream`, password `changeme`)
- Real keystore should be generated with proper credentials before release

## Key Decisions

1. **extClass uses FULL path (no leading dot)** — matches AniKoto session-49 pattern where `applicationId ≠ source package`. The loader uses `extClass` as-is when it doesn't start with `.`.
2. **versionName = "16.1"** — matches extVersionCode=1, must start with "16." (ext-lib 16 loader requirement).
3. **Added keiyoushi stubs** — neither AniKoto nor mkissa have `keiyoushi.utils` stubs (they don't use `getPreferencesLazy`), but the UniQuestream skeleton needs it.

## Verified Files

- All 29 stub files present in `stubs/src/main/kotlin/`
- `UniQuestream.kt` at correct path for `java.srcDirs("src")` source set
- `common/AndroidManifest.xml` uses `${extClass}`, `${nsfw}`, `${versionId}` placeholders
- Gradle wrapper: Gradle 8.14.3, same as AniKoto
- Plugin versions: AGP 8.13.2, Kotlin 2.2.21, same as AniKoto

## Next Steps

- [ ] Run actual Gradle build to verify compilation (needs Android SDK/JDK)
- [ ] Analyze `https://anime.uniquestream.net` to understand site structure
- [ ] Implement `popularAnimeRequest/Parse` based on site HTML/API
- [ ] Implement search, episodes, video extraction
