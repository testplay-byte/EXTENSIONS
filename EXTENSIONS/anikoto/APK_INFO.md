# AniKoto 180 — Extension APK Information Sheet

> Generated: 2026-06-26 (session 49) · By: Confused_Creature (180)
> Current version: v16.9 (versionCode=9)

---

## APK Details

| Property | Value |
|----------|-------|
| **File name** | `aniyomi-en.anikoto180-v16.9-release.apk` |
| **File size** | ~255 KB |
| **MD5** | `524c91799b7a33f56a2753678c546eee` |
| **App label** | AniKoto 180 |
| **Package name** | `eu.kanade.tachiyomi.animeextension.en.anikoto180` |
| **Version** | `16.9` (versionCode=9) |
| **Extension versionId** | `11` (STABLE — do NOT change) |
| **Extension class** | `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` (FULL path, no leading dot) |
| **ext-lib version** | 16 (versionName must start with "16.") |
| **Language** | English (en) |
| **NSFW** | false |
| **Min SDK** | 21 (Android 5.0) |
| **Target SDK** | 34 (Android 14) |
| **Compile SDK** | 34 |
| **Java version** | 17 |

---

## Signing Information

| Property | Value |
|----------|-------|
| **Signing scheme v1** (JAR) | ✅ Enabled |
| **Signing scheme v2** (APK Signature) | ✅ Enabled |
| **Signing scheme v3** | ❌ Not enabled (not needed for Aniyomi) |
| **Keystore file** | `anikoto-release.jks` |
| **Keystore type** | PKCS12 |
| **Keystore alias** | `anikoto` |
| **Keystore password** | `$KEYSTORE_PASSWORD` |
| **Key password** | `$KEYSTORE_PASSWORD` |
| **Key algorithm** | RSA 2048-bit |
| **Validity** | 10,000 days (expires ~2053) |
| **Certificate DN** | `CN=Confused_Creature, OU=180, O=AniKoto, L=Unknown, ST=Unknown, C=Unknown` |
| **Certificate SHA-256** | `B4:67:CA:64:0B:A7:9C:C0:91:D4:A9:99:00:56:70:89:95:0B:C8:27:4E:F6:4D:8F:56:2B:25:90:4A:61:6A:5A` |

### ⚠️ CRITICAL — Keep the keystore safe

The keystore file (`anikoto-release.jks`) and password (`$KEYSTORE_PASSWORD`) are required for ALL future updates. If you lose them:

- You **cannot** publish an update with the same package signature
- Users must **uninstall** the old extension before installing a new one (Android sees a different signature as a different app)
- Saved anime and watch progress linked to the old signature would be **orphaned**
- The extension ID would change (new keystore = new MD5 = new source ID)

**Back up the keystore to multiple secure locations.** The keystore file is at:
```
EXTENSIONS/anikoto/DEV/anikoto-release.jks
```

Keystore info is also saved at:
```
EXTENSIONS/anikoto/DEV/keystore-info.txt
```

The keystore is in `.gitignore` — it will NOT be committed to git.

---

## Build Configuration

| Property | Value |
|----------|-------|
| **Build type** | Release |
| **R8 minification** | Enabled (`isMinifyEnabled = true`) |
| **ProGuard rules** | Keep ALL `...anikoto.**` classes + `$$serializer` classes + serialization infrastructure |
| **debuggable** | `false` |
| **usesCleartextTraffic** | `true` (required for localhost proxy on 127.0.0.1) |
| **allowBackup** | `false` |
| **WRITE_EXTERNAL_STORAGE** | Not declared (logcat-only logging — no file I/O) |

---

## Manifest Meta-Data

| Key | Value |
|-----|-------|
| `tachiyomi.animeextension.class` | `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` |
| `tachiyomi.animeextension.nsfw` | `0` |
| `tachiyomi.animeextension.versionId` | `11` |

---

## Extension ID Stability

The Aniyomi app links saved anime to extensions via a source ID:
```
source ID = MD5("anikoto 180/en/11")
```

This ID is derived from the extension **name** (lowercase), **language**, and **versionId**. As long as:
- The extension name stays `"AniKoto 180"` (lowercased to `anikoto 180`)
- The versionId stays `11`

...the source ID is stable, and saved anime are preserved across updates.

**IMPORTANT**: 
- Do NOT change `override val name = "AniKoto 180"` — changing it changes the source ID → orphans saved anime.
- Do NOT change `versionId = 11` — same reason.
- The **package name** (`...anikoto180`) does NOT affect the source ID — it's safe to change (but users must uninstall the old package first, since Android treats different package names as different apps).

---

## Features

### Video Servers (priority order for auto-select)
1. VidCloud-1 (vidwish.live) — OkHttp
2. VidPlay-1 (vidtube.site) — OkHttp
3. Vidstream-2 (megaplay.buzz → cdn.mewstream.buzz) — WebView fallback (Chrome TLS)
4. HD-1 (megaplay.buzz) — same as Vidstream-2
5. Kiwi-Stream (mewcdn.online → vibeplayer.site) — OkHttp (when mapper provides streaming; toggleable)

### Audio Types
- SUB (subbed)
- HSUB (hardsub)
- DUB (dubbed)

### Resolutions
- 1080p, 720p, 480p, 360p (availability depends on the anime)

### Episode Metadata Enrichment
Multi-source fetching (all toggleable in settings, all default ON):
- **Thumbnails**: Anikage.cc → AniList streamingEpisodes → Kitsu → AniList banner → anime cover
- **Titles**: Jikan (MyAnimeList) → Anikage → Kitsu — format: "EP N - title"
- **Descriptions**: Anikage → Kitsu

### Settings (3 categories)
**Playback:**
- Preferred quality (1080p/720p/480p/360p) — shows "Currently: %s"
- Preferred audio (Sub/Dub/Hardsub) — shows "Currently: %s"
- Pre-fetch buffer (10%/20%/30%/50%/100%) — shows "Currently: %s"
- Preferred server (Auto/VidPlay-1/HD-1/Vidstream-2/VidCloud-1/Kiwi-Stream) — shows "Currently: %s"

**Servers:**
- Enable Kiwi-Stream (ON/OFF, default ON) — gates the mapper API call

**Episode metadata:**
- Load episode thumbnails (ON/OFF, default ON)
- Load episode titles (ON/OFF, default ON)
- Load episode descriptions (ON/OFF, default ON)

### Promotional Credit
Every anime's description ends with:
```
[empty line]
Thank the Confused_creature_180
```

### Technical Architecture
- **Self-sustained**: no backend server required — runs entirely on-device
- **LocalProxyServer**: 127.0.0.1 (on-device), PNG header stripping, LRU cache (200 entries), prefetch
- **WebViewFetcher**: uses Android WebView (Chrome's BoringSSL TLS) for Cloudflare WAF-blocked CDNs
- **Per-stream Referer**: each AudioStream carries its own Referer (derived from the iframe host)
- **CloudflareInterceptor**: inherited `client` handles 403/503 from Cloudflare servers
- **Desktop Chrome UA**: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36`
- **Fork compatibility**: `getVideoList(SEpisode)` override delegates to `getHosterList` + flattens (prevents DNS errors in legacy-pipeline forks)
- **episode.url format**: `/watch/slug/ep-N#fragment` (valid URL path + metadata in fragment)

### Logging
- **Logcat only** (tag "Anikoto"). Capture with: `adb logcat -s Anikoto:*`
- No file logging (no `Download/1118000/` folder, no `WRITE_EXTERNAL_STORAGE` permission)
- `AnikotoLog` object: `i()`, `d()`, `w()`, `e(msg, throwable?)`, `trunc()` methods

---

## DEX Verification

| Check | Result |
|-------|--------|
| Stub! count | 0 ✅ |
| Anikoto class | Present at `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` ✅ |
| extClass | `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` (full path, no dot) ✅ |
| `$$serializer` classes | 23 refs ✅ (prevents R8 serialization crash) |
| WebViewFetcher class | Present ✅ |
| EpisodeMetadataFetcher class | Present ✅ |
| LocalProxyServer class | Present ✅ |
| AnikotoExtractors class | Present ✅ |
| R8 obfuscation | Active (internal classes obfuscated, extension classes kept) ✅ |
| Icons | 5 densities (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) ✅ |
| "Thank the Confused_creature_180" | Present ✅ |
| 3 settings categories (Playback, Servers, Episode metadata) | Present ✅ |
| "Currently: %s" on all 4 dropdowns | Present ✅ |

---

## Source Code Location

| Component | Path |
|-----------|------|
| Extension source | `EXTENSIONS/anikoto/DEV/src/en/anikoto/src/main/kotlin/eu/kanade/tachiyomi/animeextension/en/anikoto/` |
| Video pipeline | `.../anikoto/video/` — AnikotoExtractors.kt, LocalProxyServer.kt, WebViewFetcher.kt, Models.kt |
| Metadata fetcher | `.../anikoto/metadata/` — EpisodeMetadataFetcher.kt |
| Episode meta encoding | `.../anikoto/EpisodeMeta.kt` |
| RC4 encoding | `.../anikoto/AnikotoRC4.kt` |
| Filters | `.../anikoto/AnikotoFilters.kt` |
| Logger | `.../anikoto/AnikotoLog.kt` |
| DTOs | `.../anikoto/AnikotoDto.kt` |
| Stubs module | `EXTENSIONS/anikoto/DEV/stubs/` |
| Build config | `EXTENSIONS/anikoto/DEV/src/en/anikoto/build.gradle.kts` |
| Manifest | `EXTENSIONS/anikoto/DEV/common/AndroidManifest.xml` |
| ProGuard rules | `EXTENSIONS/anikoto/DEV/common/proguard-rules.pro` |
| Keystore | `EXTENSIONS/anikoto/DEV/anikoto-release.jks` |
| Keystore info | `EXTENSIONS/anikoto/DEV/keystore-info.txt` |
| Env script | `/home/z/my-project/.android-env.sh` |

---

## Build Commands

```bash
# Set up environment (every new shell)
source /home/z/my-project/.android-env.sh

cd /home/z/my-project/EXTENSIONS/anikoto/DEV

# Build debug APK (for testing — no R8, easier logs)
./gradlew :src:en:anikoto:assembleDebug --no-daemon
# → src/en/anikoto/build/outputs/apk/debug/aniyomi-en.anikoto180-v16.9-debug.apk (~302KB)

# Build signed release APK (for publishing — R8 minified + signed)
./gradlew :src:en:anikoto:assembleRelease --no-daemon
# → src/en/anikoto/build/outputs/apk/release/aniyomi-en.anikoto180-v16.9-release.apk (~255KB)

# Verify signing
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose --print-certs \
  src/en/anikoto/build/outputs/apk/release/aniyomi-en.anikoto180-v16.9-release.apk
# Should show: Verifies, v1+v2 true, SHA-256 b467ca64...
```

---

## Security Notes

### What protects the extension from copying
1. **R8 minification** — obfuscates internal code (extension classes kept for Aniyomi compatibility, but internal logic is harder to read)
2. **Signed keystore** — someone can't repackage the APK with their own key without changing the package signature (Android rejects signature mismatches on update)
3. **The code is compiled to DEX** — not source code, requires reverse engineering (jadx) to read

### What does NOT protect it
1. **API endpoints** — visible via network proxy (mitmproxy) regardless of code protection
2. **The extension logic** — a determined reverse engineer with jadx + frida can read the code
3. **The keystore password** — stored in the build config (but not in the APK itself)

---

## Author

**Confused_Creature** (code name: 180 / 1118000)

---

*This document contains all critical information about the AniKoto 180 extension APK v16.9. Keep it with the keystore backup.*
