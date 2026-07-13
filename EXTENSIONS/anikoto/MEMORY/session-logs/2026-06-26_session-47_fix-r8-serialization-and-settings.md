# Session 47 — Fix R8 Serialization Crash + Settings Reorganization (v16.7)

> Date: 2026-06-26 · Session #: 47 · Duration: ~medium · Timezone: Asia/Karachi
> Type: CRITICAL BUG FIX (video playback crash) + UI FIX (settings)
> Follows: session 46 (release build v16.6)

## Goal

Fix two issues reported by the user after testing the v16.6 release:
1. **CRITICAL: Video playback crashes immediately** — "no available videos" + error
   "type reference constructed without actual type information"
2. **Settings UI issues** — Preferred server in wrong section, missing "Currently: %s"
   summaries, Kiwi-Stream description too technical

## Root cause analysis — the video playback crash

### The error
```
type reference constructed without actual type information
```

This is a `kotlinx.serialization` error thrown when the runtime can't find the generated
`$$serializer` class for a `@Serializable` type. When `json.decodeFromString<ServerListResponse>(...)`
is called, kotlinx.serialization looks up `ServerListResponse$$serializer` via reflection.
If R8 stripped or renamed that class, the lookup fails → exception → caught by try-catch
in `getHosterList` → empty task list → empty hoster list → "no available videos".

### Why it only happened in release builds
- **Debug builds**: no R8 minification → all classes preserved → serialization works
- **Release builds**: R8 minification active → `$$serializer` classes stripped → serialization fails

### Why the old proguard rules didn't work
The old rule `-keep @kotlinx.serialization.Serializable class ** { *; }` keeps the DTO classes
themselves (the ones annotated with `@Serializable`), but the generated `$$serializer` classes
are **NOT** annotated with `@Serializable` — they're compiler-generated companion classes.
R8 was free to strip them, and it did.

### Verification (DEX analysis of v16.6 release)
- v16.6 release DEX had 0 `$$serializer` references → all stripped → crash
- v16.7 release DEX has 23 `$$serializer` references → all preserved → fixed

## The fix

### 1. Comprehensive ProGuard rules (`common/proguard-rules.pro`)

Rewrote the entire file with 3 layers of protection:

**Layer 1 — Keep ALL extension classes:**
```proguard
-keep class eu.kanade.tachiyomi.animeextension.en.anikoto.** { *; }
```
This keeps every class in the anikoto package (including nested DTOs, the `.metadata`
and `.video` subpackages, and all generated serializers) with all their members.

**Layer 2 — Keep generated serializers globally:**
```proguard
-keep class **$$serializer { *; }
-keepclassmembers class **$$serializer { *; }
```
Catches any `$$serializer` class anywhere in the APK (belt + suspenders).

**Layer 3 — Keep serialization infrastructure:**
```proguard
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class ** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
-keepclassmembers class ** { @kotlinx.serialization.SerialName <fields>; }
```
- `Signature` attribute is critical for generic type resolution
- `Companion` objects hold the `serializer()` method
- `@SerialName` fields must not be renamed (case-sensitive JSON mapping like `"Media"` vs `"media"`)

## The settings fix

### What the user reported
1. "Preferred server" was in its own "Servers" section — should be back in Playback
2. Playback dropdowns lost their "Currently: %s" summary (shows selected value)
3. Enable Kiwi-Stream description too technical ("mapper API") — want simpler style

### What was changed

**Reorganized from 3 categories → 2 categories:**
```
┌─ Playback ──────────────────────────────────┐
│  Preferred quality         Currently: 720p   │  ← summary restored
│  Preferred audio           Currently: Sub    │  ← summary restored
│  Pre-fetch buffer          Currently: 10%    │  ← summary restored
│  Preferred server          Currently: Auto   │  ← moved back here + summary restored
│  Enable Kiwi-Stream              [ON]        │  ← moved here + description simplified
├─ Episode metadata ──────────────────────────┤
│  Load episode thumbnails         [ON]        │  (unchanged)
│  Load episode titles             [ON]        │  (unchanged)
│  Load episode descriptions       [ON]        │  (unchanged)
└──────────────────────────────────────────────┘
```

**All 4 dropdowns** now have `summary = "Currently: %s"` (shows the selected value, no verbose text).

**Enable Kiwi-Stream description simplified:**
- Before: `"Fetching Kiwi-Stream from the mapper API"` / `"Kiwi-Stream disabled (mapper API not called)"`
- After: `"Fetching Kiwi-Stream from external sources"` / `"Kiwi-Stream disabled"`
- Now matches the style of the other toggles ("Fetching preview images from external sources", etc.)

## Verification

### Build
```
./gradlew :src:en:anikoto:assembleRelease → BUILD SUCCESSFUL (255KB, R8)
./gradlew :src:en:anikoto:assembleDebug → BUILD SUCCESSFUL (302KB)
```

### Release APK metadata
- `versionCode=7`, `versionName=16.7` ✅
- `application-label='AniKoto 180'` ✅
- Signing: v1+v2 verified, SHA-256 `b467ca640ba79cc0...` ✅ matches keystore

### DEX content verification (THE critical check)
| Check | Result |
|---|---|
| Stub! count | 0 ✅ |
| **`$$serializer` classes** | **23 refs ✅** (was 0 in v16.6 — this is the fix) |
| DTO class names preserved (8 checked) | all PRESENT ✅ |
| "type reference" error string | 0 (not in our DEX) ✅ |
| "Currently: %s" summary | PRESENT ✅ |
| "Servers" category | removed (0 matches) ✅ |
| "Playback" category | PRESENT ✅ |
| "Episode metadata" category | PRESENT ✅ |
| New Kiwi-Stream description ("from external sources") | PRESENT ✅ |
| Old Kiwi-Stream description ("mapper API") | removed ✅ |
| All 8 preference keys | all PRESENT ✅ |

### MD5
- Release: `e410f65d23cf8295bc05ccbc1f9fad1f`
- Debug: `28ce2f3de9cb5bf4ab0313f075cc167f`

## What changed for the user

| Before (v16.6 release) | After (v16.7 release) |
|---|---|
| Opening an episode → immediate crash → "no available videos" | **Video playback works** (serializers preserved) |
| Preferred server in its own "Servers" section | **Back in Playback** (with the other playback prefs) |
| Dropdowns had no summary (no current value shown) | **"Currently: %s"** on all 4 dropdowns |
| Kiwi-Stream: "Fetching Kiwi-Stream from the mapper API" | **"Fetching Kiwi-Stream from external sources"** (simpler) |
| 3 categories (Playback, Servers, Episode metadata) | **2 categories** (Playback, Episode metadata) |

## Files changed

| File | Change |
|---|---|
| `common/proguard-rules.pro` | Complete rewrite — keep all extension classes + `$$serializer` classes + serialization infrastructure |
| `Anikoto.kt` | `setupPreferenceScreen` — 3→2 categories, moved Preferred server + Enable Kiwi-Stream to Playback, added "Currently: %s" to all 4 dropdowns, simplified Kiwi-Stream description |
| `build.gradle.kts` | `extVersionCode` 6 → 7 |
| `AnikotoLog.kt` | `EXTENSION_VERSION` → `v16.7` |

## Status

- ✅ Release APK built: `aniyomi-en.anikoto-v16.7-release.apk` (255KB, signed, R8)
- ✅ Debug APK built: `aniyomi-en.anikoto-v16.7-debug.apk` (302KB, for testing)
- ✅ Both synced to `WORKSPACE/APK/` + `WORKSPACE/DEV/ANIKOTO/APK/`
- ✅ DEX verified: 23 `$$serializer` classes present (the fix), all DTOs preserved, all settings strings correct
- ✅ Signing verified (SHA-256 matches keystore)
- ⏳ User to test: install release v16.7 → open an episode → should play (no crash). Check settings: 2 categories, "Currently: %s" on dropdowns, Kiwi-Stream in Playback section.
