# Session 49 — Change Package Name to ...anikoto180 (v16.9)

> Date: 2026-06-26 · Session #: 49 · Duration: ~short · Timezone: Asia/Karachi
> Type: IDENTITY CHANGE — package name ...anikoto → ...anikoto180
> Follows: session 48 (3 settings categories, v16.8)

## Goal

Change the Android package name from `eu.kanade.tachiyomi.animeextension.en.anikoto` to
`eu.kanade.tachiyomi.animeextension.en.anikoto180`. Reason: other publishers also use
"anikoto" as their package name — adding "180" distinguishes this extension at the Android
level (different package = different app, no install conflict with others).

## What was changed

### `build.gradle.kts`
1. **`applicationIdSuffix`**: `"en.anikoto"` → `"en.anikoto180"`
   - Full applicationId is now: `eu.kanade.tachiyomi.animeextension.en.anikoto180`
2. **`extClass`**: `".Anikoto"` → `"eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto"` (full path, no leading dot)
   - Critical: the loader does `packageName + extClass` when extClass starts with `.`. With the new
     packageName `...anikoto180`, a relative `.Anikoto` would look for `...anikoto180.Anikoto`
     which doesn't exist → ClassNotFoundException. The full path makes the loader use it as-is.
   - Verified in the loader source: `REFERENCE_HUB/aniyomi-app/.../AnimeExtensionLoader.kt:297-301`
3. **`archivesName`**: `"aniyomi-en.anikoto-v..."` → `"aniyomi-en.anikoto180-v..."` (filename consistency)
4. **`extVersionCode`**: 8 → 9

### `AnikotoLog.kt`
- `EXTENSION_VERSION` → `v16.9`

### What was NOT changed
- **Source code package**: stays at `eu.kanade.tachiyomi.animeextension.en.anikoto` (no files moved)
- **`name` property**: stays `"AniKoto 180"` → source ID stays `MD5("anikoto 180/en/11")` (unchanged)
- **`versionId`**: stays 11 STABLE
- **Keystore**: same `anikoto-release.jks` (SHA-256 `b467ca64...`)
- **ProGuard rules**: unchanged (keep `...anikoto.**` — the source package)
- **Manifest**: unchanged (uses `${extClass}` placeholder, automatically gets the full path)

## Verification

### Build
```
./gradlew :src:en:anikoto:assembleRelease :src:en:anikoto:assembleDebug --no-daemon
> BUILD SUCCESSFUL in 1m 3s
```

### APK metadata (release)
| Check | Result |
|---|---|
| Package name | `eu.kanade.tachiyomi.animeextension.en.anikoto180` ✅ (was ...anikoto) |
| versionCode / versionName | 9 / 16.9 ✅ |
| App label | `AniKoto 180` ✅ (unchanged) |
| extClass in manifest | `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` ✅ (full path, no dot) |
| versionId | 11 STABLE ✅ |
| Signing v1 + v2 | Verified ✅ |
| Certificate SHA-256 | `b467ca640ba79cc0...` ✅ (same keystore) |
| Certificate DN | `CN=Confused_Creature, OU=180, O=AniKoto` ✅ |

### DEX content verification
| Check | Result |
|---|---|
| Stub! count | 0 ✅ |
| Anikoto class at OLD source package (`...anikoto.Anikoto`) | 31 refs ✅ (source code didn't move) |
| `$$serializer` classes (R8 fix from s47) | 23 refs ✅ (no regression) |
| 3 settings categories (Playback, Servers, Episode metadata) | all PRESENT ✅ |

### MD5
- Release: `5a84dfeeea8840adfdc738a988b5d859`
- Debug: `7cffc6ec02868ece9cd90e116a4e03e3`

## How the loader finds the class (verified)

Read `REFERENCE_HUB/aniyomi-app/app/src/main/java/eu/kanade/tachiyomi/extension/anime/util/AnimeExtensionLoader.kt:293-302`:

```kotlin
val sources = appInfo.metaData.getString(METADATA_SOURCE_CLASS)!!
    .split(";")
    .map {
        val sourceClass = it.trim()
        if (sourceClass.startsWith(".")) {
            pkgInfo.packageName + sourceClass    // RELATIVE: prepend packageName
        } else {
            sourceClass                           // ABSOLUTE: use as-is
        }
    }
    .flatMap {
        Class.forName(it, false, classLoader)...
    }
```

With our new setup:
- `pkgInfo.packageName` = `eu.kanade.tachiyomi.animeextension.en.anikoto180`
- `METADATA_SOURCE_CLASS` (from manifest) = `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto`
- Since it does NOT start with `.`, the loader uses it as-is
- `Class.forName("eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto")` → finds the class ✅

## What this means for users

### ⚠️ CRITICAL: Existing users must UNINSTALL before installing

The package name changed (`...anikoto` → `...anikoto180`). Android treats different package names
as **completely different apps** — there is no "package rename update" mechanism, even with the
same keystore.

**What users must do:**
1. **Uninstall** the old AniKoto 180 extension (the `...anikoto` package)
2. **Install** the new v16.9 APK (the `...anikoto180` package)

**What is preserved (same source ID):**
- ✅ Saved anime (linked by source ID = `MD5("anikoto 180/en/11")` — unchanged)
- ✅ User settings (stored in the Aniyomi app's SharedPreferences, keyed by `"source_$id"`)
- ✅ Watch progress (linked by source ID)

**What is NOT preserved:**
- ❌ The old APK itself (must be uninstalled — can't have both installed)
- The Aniyomi app will re-link the saved anime to the new package automatically (same source ID)

### ⚠️ Don't install both at the same time
If a user installs v16.9 (`...anikoto180`) without uninstalling v16.6/7/8 (`...anikoto`):
- Both are installed simultaneously
- Both produce the **same source ID** (same name/lang/versionId)
- The Aniyomi app sees two packages with the same source ID → conflict (might pick one, show duplicates, or behave unpredictably)
- **Always uninstall the old one first**

## Documentation updated
- `MEMORY/guides/04-build-checklist.md` §1: rewrote the extClass rule to document the new full-path pattern (and why it's needed when applicationId ≠ source package)
- `MEMORY/guides/04-build-checklist.md` §4: updated WRITE_EXTERNAL_STORAGE line (removed in s46, was stale)

## Files changed

| File | Change |
|---|---|
| `build.gradle.kts` | `applicationIdSuffix` → `en.anikoto180`; `extClass` → full path; `archivesName` → `anikoto180`; `extVersionCode` 8→9 |
| `AnikotoLog.kt` | `EXTENSION_VERSION` → `v16.9` |
| `MEMORY/guides/04-build-checklist.md` | §1 extClass rule rewritten (full path pattern); §4 WRITE_EXTERNAL_STORAGE line updated |

## Status

- ✅ Release APK built: `aniyomi-en.anikoto180-v16.9-release.apk` (255KB, signed)
- ✅ Debug APK built: `aniyomi-en.anikoto180-v16.9-debug.apk` (302KB)
- ✅ Both synced to `WORKSPACE/APK/` + `WORKSPACE/DEV/ANIKOTO/APK/`
- ✅ Package name verified: `...anikoto180` ✅
- ✅ extClass verified: full path `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` ✅
- ✅ Class loads via the absolute-path loader branch (verified against loader source)
- ✅ Signing verified (same keystore, SHA-256 matches)
- ✅ No regressions (serializers preserved, settings 3 categories intact, Stub!=0)
- ⏳ User to test: **uninstall old extension first**, then install v16.9 → verify it loads, saved anime appear, episodes play
