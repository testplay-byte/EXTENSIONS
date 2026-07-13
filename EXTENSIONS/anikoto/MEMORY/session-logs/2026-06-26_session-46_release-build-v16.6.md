# Session 46 — Build Signed Release APK v16.6 (AniKoto 180)

> Date: 2026-06-26 · Session #: 46 · Duration: ~medium · Timezone: Asia/Karachi
> Type: RELEASE BUILD — signed, R8, published-name match, logcat-only logging, new icon
> Follows: session 45 (Kiwi-Stream toggle, v16.29 dev)

## Goal

Build the first publishable release APK from the restored project. Match the published v16.5's
identity (name "AniKoto 180", source ID, signing keystore). Switch to logcat-only logging
(no Download/1118000/ folder). Apply the new app icon. Release version: 16.6.

## What was done

### 1. Keystore setup
- User provided `anikoto-release.jks` + `keystore-info.txt` (uploaded to `/upload/`)
- Verified keystore: PKCS12, alias=anikoto, SHA-256=`B4:67:CA:64:0B:A7:9C:C0:91:D4:A9:99:00:56:70:89:95:0B:C8:27:4E:F6:4D:8F:56:2B:25:90:4A:61:6A:5A` ✅ matches APK_INFO
- Placed at `DEVELOPMENT_CODE/anikoto-release.jks` (referenced via `rootProject.file(...)`)
- Added `*.jks` + `keystore-info.txt` to `.gitignore` (never commit the keystore)

### 2. App icon
- User provided `Anikoto_Icon.png` (1000×1000 RGBA)
- Generated 5 density PNGs with Pillow (LANCZOS resampling):
  - mipmap-mdpi: 48×48 (5.5KB)
  - mipmap-hdpi: 72×72 (11KB)
  - mipmap-xhdpi: 96×96 (19KB)
  - mipmap-xxhdpi: 144×144 (42KB)
  - mipmap-xxxhdpi: 192×192 (73KB)
- Replaced the old placeholder icons in `res/mipmap-*/ic_launcher.png`

### 3. Name + source ID match
- Changed `override val name = "Anikoto"` → `override val name = "AniKoto 180"`
- Changed `extName = "Anikoto"` → `extName = "AniKoto 180"` in build.gradle.kts
- Changed `manifestPlaceholders["appName"] = "Aniyomi: $extName"` → `manifestPlaceholders["appName"] = extName` (removed "Aniyomi: " prefix — matches published v16.5)
- Source ID is now `MD5("anikoto 180/en/11")` — **matches published v16.5** ✅ (saved anime preserved for v16.5 users)

### 4. Logcat-only logging (removed file logging entirely)
- Simplified `AnikotoLog.kt` from 130 lines → 48 lines
- Removed: `ensureLogFile()`, `writeSessionHeader()`, `LOG_DIR_NAME`, file I/O, `Injekt`/`Application`/`File`/`SimpleDateFormat`/`Date`/`Locale`/`Build` imports
- Kept: `i()`, `d()`, `w()`, `e()`, `trunc()` API (delegates to `android.util.Log`)
- All logging now goes to logcat (tag "Anikoto") — capture with `adb logcat -s Anikoto:*`
- Removed `WRITE_EXTERNAL_STORAGE` permission from `AndroidManifest.xml` (no longer needed)

### 5. Build config
- `extVersionCode = 6` (continues published sequence: v16.5=5 → v16.6=6)
- `versionName = "16.6"`
- Added `signingConfigs { create("release") { ... } }` using the keystore
- Wired release build type: `signingConfig = signingConfigs.getByName("release")`
- Fixed proguard-rules.pro syntax: `{ * }` → `{ *; }` (R8 requires semicolon)
- `buildConfig = false` (unchanged — we don't use BuildConfig flags)

### 6. Built both APKs
- **Release:** `./gradlew :src:en:anikoto:assembleRelease` → 233KB, signed, R8 minified
- **Debug:** `./gradlew :src:en:anikoto:assembleDebug` → 302KB, unsigned, no R8 (for testing)

## Verification

### Release APK metadata
| Check | Result |
|---|---|
| File | `aniyomi-en.anikoto-v16.6-release.apk` (233,257 bytes) |
| versionCode | 6 ✅ |
| versionName | 16.6 ✅ |
| Package | `eu.kanade.tachiyomi.animeextension.en.anikoto` ✅ |
| App label | `AniKoto 180` ✅ (matches published v16.5) |
| extClass | `.Anikoto` (not doubled) ✅ |
| versionId | 11 (STABLE) ✅ |
| NSFW | 0 ✅ |
| MD5 | `8692c963a363138499d89f5195245595` |

### Signing verification
| Check | Result |
|---|---|
| v1 scheme (JAR) | ✅ Verified |
| v2 scheme (APK Signature) | ✅ Verified |
| Certificate DN | `CN=Confused_Creature, OU=180, O=AniKoto` ✅ |
| Certificate SHA-256 | `b467ca640ba79cc091d4a99900567089950bc8274ef64d8f562b25904a616a5a` ✅ matches APK_INFO |

### DEX content verification
| Check | Result |
|---|---|
| Stub! count | 0 ✅ (single DEX — R8 merged) |
| File logging removed (Download/1118000, ensureLogFile, etc.) | all 0 ✅ |
| WRITE_EXTERNAL_STORAGE | not in manifest ✅ |
| "AniKoto 180" name | PRESENT ✅ |
| Kiwi-Stream toggle (pref_enable_kiwi, Enable Kiwi-Stream) | PRESENT ✅ |
| getVideoList (legacy override) | PRESENT ✅ |
| /watch/ (EpisodeMeta new format) | PRESENT ✅ |
| filter?keyword= (search fix) | PRESENT ✅ |
| EpisodeMetadataFetcher | PRESENT ✅ |
| WebViewFetcher | PRESENT ✅ |
| R8 obfuscation | active (69 obfuscated class names) ✅ |
| Icons | 5 densities (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) ✅ |

## What changed for the user

| Before (dev v16.29) | After (release v16.6) |
|---|---|
| name = "Anikoto" (different source ID) | **name = "AniKoto 180"** (matches published v16.5 — saved anime preserved) |
| App label = "Aniyomi: Anikoto" | **App label = "AniKoto 180"** (no prefix, matches published) |
| File logging to Download/1118000/ | **Logcat only** (no folder, no permission) |
| WRITE_EXTERNAL_STORAGE permission | **Removed** |
| Placeholder icons | **New app icon** (user-provided, 5 densities) |
| Unsigned debug builds | **Signed release build** (with keystore, R8 minified) |
| versionCode=29 (dev sequence) | **versionCode=6** (published sequence: 5→6) |

## ⚠️ Important notes for the user

### versionCode conflict
The release v16.6 has versionCode=6. Our dev builds (v16.25-v16.29) used versionCode 25-29.
Android blocks versionCode downgrades. Users who installed any dev build (v16.25+) must
**uninstall it first** before installing this release. The published v16.5 (versionCode=5)
upgrades to v16.6 (versionCode=6) normally.

### Future versioning
- Future dev builds should continue from versionCode=7 (not 30+)
- Future releases: v16.7=versionCode 7, v16.8=versionCode 8, etc.
- The pattern: versionName = "16." + versionCode

### Keystore safety
The keystore is at `DEVELOPMENT_CODE/anikoto-release.jks`. It's in `.gitignore` (won't be
committed). **Back it up to multiple secure locations.** If lost, users must uninstall the
old extension before installing a new one (signature mismatch).

### Debug APK for testing
A debug APK is also built at `WORKSPACE/DEV/ANIKOTO/APK/aniyomi-en.anikoto-v16.6-debug.apk`.
Use this for testing (no R8 obfuscation → easier to read logs via `adb logcat -s Anikoto:*`).
The release APK is for publishing.

## Files changed

| File | Change |
|---|---|
| `Anikoto.kt` | `name` → "AniKoto 180" |
| `AnikotoLog.kt` | Completely rewritten — logcat-only (removed file logging, 130→48 lines) |
| `common/AndroidManifest.xml` | Removed WRITE_EXTERNAL_STORAGE permission |
| `common/proguard-rules.pro` | Fixed R8 syntax: `{ * }` → `{ *; }` |
| `src/en/anikoto/build.gradle.kts` | extName→"AniKoto 180", appName=extName (no prefix), versionCode=6, versionName="16.6", signingConfigs, release signingConfig |
| `src/en/anikoto/res/mipmap-*/ic_launcher.png` | 5 new icon densities (generated from Anikoto_Icon.png) |
| `DEVELOPMENT_CODE/anikoto-release.jks` | NEW — keystore file (from user upload) |
| `DEVELOPMENT_CODE/keystore-info.txt` | NEW — keystore info doc |
| `.gitignore` | Added `*.jks` + `keystore-info.txt` |

## Status

- ✅ Release APK built: `aniyomi-en.anikoto-v16.6-release.apk` (233KB, signed, R8)
- ✅ Debug APK built: `aniyomi-en.anikoto-v16.6-debug.apk` (302KB, unsigned, for testing)
- ✅ Both synced to `WORKSPACE/APK/` + `WORKSPACE/DEV/ANIKOTO/APK/`
- ✅ Signing verified (v1+v2, SHA-256 matches published v16.5 keystore)
- ✅ All DEX content checks pass
- ✅ Name + source ID match published v16.5
- ✅ File logging completely removed (logcat only)
- ✅ New app icon applied (5 densities)
- ✅ Keystore protected in .gitignore
- ⏳ User to publish the release APK
