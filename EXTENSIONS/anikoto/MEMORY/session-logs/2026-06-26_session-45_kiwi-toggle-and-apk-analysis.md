# Session 45 — Add Enable Kiwi-Stream Toggle (v16.29)

> Date: 2026-06-26 · Session #: 45 · Duration: ~medium · Timezone: Asia/Karachi
> Type: FEATURE ADD — user-toggleable Kiwi-Stream (mapper API)
> Follows: session 44 (settings UI redesign, v16.28)

## Goal

Add an "Enable Kiwi-Stream" toggle to the extension settings (default ON), and properly
gate the mapper API (PATH B) with it. The user asked whether Kiwi-Stream disabling was
"properly handled" — it was NOT (the mapper was always called). This session adds it.

Also analyzed the published APK info (`upload/APK_INFO.md`) and flagged two critical
discrepancies (see "Analysis" section at the end + separate guidance doc).

## What was wrong (before v16.29)

The mapper API (`https://mapper.nekostream.site/api/mal/...`) — PATH B in `getHosterList` —
was called unconditionally whenever `malId`/`epNum`/`timestamp` were present in the
EpisodeMeta. There was no user-facing toggle to disable it. This meant:
- Users who don't want Kiwi-Stream couldn't opt out
- An extra network call to a third-party domain happened on every episode open
- The mapper API could be slow/unavailable and delay the Hoster list

## The fix (v16.29)

### 1. New preference constant + getter

```kotlin
companion object {
    ...
    // ★ session 45 (v16.29): Kiwi-Stream toggle (default ON)
    private const val PREF_ENABLE_KIWI_KEY = "pref_enable_kiwi"
    private const val PREF_ENABLE_KIWI_DEFAULT = true
}

private val enableKiwi: Boolean
    get() = preferences.getBoolean(PREF_ENABLE_KIWI_KEY, PREF_ENABLE_KIWI_DEFAULT)
```

### 2. Gate PATH B in `getHosterList`

```kotlin
// ── Discovery B: mapper API (Kiwi-Stream) ─────────────────────────
// ★ session 45 (v16.29): gated by the enableKiwi toggle (default ON).
// When OFF, the mapper API is not called at all — no Kiwi-Stream servers.
if (!enableKiwi) {
    AnikotoLog.i("PATH B: skipped (Kiwi-Stream disabled in settings)")
} else if (meta.malId.isNotEmpty() && meta.epNum.isNotEmpty() && meta.timestamp.isNotEmpty()) {
    // ... existing mapper API call ...
}
```

When OFF:
- No network call to `mapper.nekostream.site`
- No Kiwi-Stream HosterTasks added to the list
- The Hoster list contains only PATH A servers (VidPlay-1, HD-1, Vidstream-2, VidCloud-1)
- If the user has "Kiwi-Stream" set as preferred server, the sort gracefully falls through
  to default order (no crash — `compareByDescending` just doesn't find a match)

### 3. Added toggle to the "Servers" category in `setupPreferenceScreen`

```kotlin
androidx.preference.SwitchPreferenceCompat(context).apply {
    key = PREF_ENABLE_KIWI_KEY
    title = "Enable Kiwi-Stream"
    summaryOn = "Fetching Kiwi-Stream from the mapper API"
    summaryOff = "Kiwi-Stream disabled (mapper API not called)"
    setDefaultValue(PREF_ENABLE_KIWI_DEFAULT)
}.also(::addPreference)
```

Placed in the "Servers" category right below "Preferred server" — logical grouping.

## Verification (per guide §7)

### §7.1 Build
```
./gradlew :src:en:anikoto:assembleDebug --no-daemon
> BUILD SUCCESSFUL in 38s
APK: aniyomi-en.anikoto-v16.29-debug.apk (156,530 bytes)
MD5: 076eb562fd8388ae3c0fa89d11863531
```

### §7.2 APK metadata
- `versionCode=29`, `versionName=16.29` ✅
- `versionId=11` STABLE ✅
- `extClass=.Anikoto` (not doubled) ✅

### §7.3 DEX content verification
| Check | Result |
|---|---|
| Stub! count (all 4 DEX) | 0, 0, 0, 0 ✅ |
| `pref_enable_kiwi` key present | ✅ classes2.dex |
| "Enable Kiwi-Stream" title present | ✅ classes2.dex |
| "Fetching Kiwi-Stream from the mapper API" (summaryOn) | ✅ |
| "Kiwi-Stream disabled (mapper API not called)" (summaryOff) | ✅ |
| "PATH B: skipped (Kiwi-Stream disabled in settings)" log string | ✅ |
| All 7 existing pref keys still present (no regressions) | ✅ all 7 |
| 3 category titles still present (Playback, Servers, Episode metadata) | ✅ all 3 |

## What changed for the user

| Before (v16.28) | After (v16.29) |
|---|---|
| Mapper API always called → Kiwi-Stream always fetched | **New "Enable Kiwi-Stream" toggle** (default ON) in Servers category |
| No way to opt out of Kiwi-Stream | Toggle OFF → mapper API not called, no Kiwi-Stream server in list |
| Extra network call on every episode open (even if unwanted) | When OFF: one less network call per episode (faster Hoster list) |

## What did NOT change

- `versionId` stays at 11 STABLE
- All other preferences unchanged
- When toggle is ON (default): behavior identical to v16.28 (Kiwi-Stream works as before)
- PATH A (primary server list) unchanged
- All video pipeline logic unchanged

## Analysis: Published APK vs current dev code

Read `upload/APK_INFO.md` (the user's published v16.5 APK info). Found **two critical
discrepancies** between the published APK and our current dev code:

### Discrepancy 1: Extension `name` property → DIFFERENT source IDs ⚠️ CRITICAL

| | Published v16.5 | Current dev v16.29 |
|---|---|---|
| `name` property | `"AniKoto 180"` | `"Anikoto"` |
| Source ID formula | MD5("anikoto 180/en/11") | MD5("anikoto/en/11") |
| Source ID | one value | **DIFFERENT value** |

**Impact:** Users who installed the published v16.5 and saved anime — those saved anime
are linked to the v16.5 source ID. If they install our v16.29, the source ID is DIFFERENT,
so Aniyomi treats it as a completely different extension → **saved anime are orphaned**
(won't appear under the new extension).

The APK_INFO explicitly warns: *"Keep `override val name = "AniKoto 180"` unchanged for
all future versions."* But the restored dev code has `name = "Anikoto"`.

**This needs a decision from the user:** should we change `name` back to `"AniKoto 180"`
to match the published APK (preserving saved anime for v16.5 users)? Or keep `"Anikoto"`
(rebranding, but v16.5 users lose saved anime)?

### Discrepancy 2: Keystore file missing ⚠️ CRITICAL

The APK_INFO says the keystore is at:
```
WORKSPACE/DEV/ANIKOTO/DEVELOPMENT_CODE/anikoto-release.jks
```

But this file is **NOT in the workspace** — it didn't survive the backup (binaries don't
survive). Without this keystore:
- `./gradlew :src:en:anikoto:assembleRelease` produces an UNSIGNED APK (won't install)
- Cannot publish an update with the same package signature
- Users would have to uninstall the old extension before installing a new one
- Saved anime + watch progress linked to the old signature would be orphaned

**The user needs to provide the keystore file** (`anikoto-release.jks`) before we can build
publishable release APKs. Details:
- Keystore type: PKCS12
- Alias: `anikoto`
- Keystore password: `Confused1118000Creature.xyz`
- Key password: `Confused1118000Creature.xyz`

### Other notes from APK_INFO analysis

- The published v16.5 APK has `WRITE_EXTERNAL_STORAGE` **NOT declared** (file logging
  disabled for release). Our current manifest HAS it (`maxSdkVersion=28`). This is fine for
  debug builds but should be removed for release builds.
- The published v16.5 has `debuggable=false` (release). Our debug builds are `debuggable=true`.
- The published v16.5 was built with R8 minification enabled. Our `build.gradle.kts` already
  has `isMinifyEnabled = true` for release — good.
- The published v16.5 has app label "AniKoto 180". Our current `extName = "Anikoto"` in
  build.gradle.kts produces app label "Aniyomi: Anikoto". This is a display-name difference
  (doesn't affect source ID, but affects what users see).

## Files changed this session

| File | Change |
|---|---|
| `Anikoto.kt` | Added `PREF_ENABLE_KIWI_KEY`/`DEFAULT` constants; added `enableKiwi` getter; gated PATH B with `if (!enableKiwi)` check; added SwitchPreferenceCompat to Servers category |
| `build.gradle.kts` | `extVersionCode` 28 → 29 |
| `AnikotoLog.kt` | `EXTENSION_VERSION` → `v16.29` |

## Status

- ✅ Build successful (v16.29, 156KB APK)
- ✅ All §7 verification checks pass
- ✅ DEX verified: toggle key + title + summaries + log string all present; no regressions
- ✅ APK synced to both folders
- ⏳ User to test on device: open settings → Servers category → verify "Enable Kiwi-Stream" toggle appears (default ON) → turn OFF → open an episode → verify no Kiwi-Stream server in the list + faster load (one less API call)
- ⏳ **User decisions needed** (see Analysis section):
  1. Should `name` be changed to `"AniKoto 180"` to match published v16.5?
  2. Please provide the `anikoto-release.jks` keystore file for publishable release builds
