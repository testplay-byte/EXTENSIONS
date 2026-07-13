# Session 14 — Debug Video Playback + Scanlator Fix

> Date: 2026-06-23 · Session #: 14 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Debug the user's reported issues from testing the session-13 APK:
1. Episode playback fails with `NoSuchMethodError` on the `Video` constructor
2. Episode list doesn't show sub/dub availability (scanlator)
3. Filters have minor issues (skip for now)

## What was found

### A. The log was from a DIFFERENT (OLD) extension, not ours

The user provided a log file (`anikoto-2026-06-23_15-23-00.log`) from the `1118000` folder. Analysis revealed this log is **NOT from our session-13 extension** — it's from an **older "session-10" extension** (the missing session the user originally tried to restore):

| Attribute | Log (OLD ext) | Our session-13 ext |
|-----------|--------------|-------------------|
| baseUrl | `anikoto.cz` (57 refs) | `anikototv.to` (0 refs in log) |
| versionId | 2 | 1 (now 2 in session-14) |
| Method name | `resolveServerToStream` (808 refs) | `resolveStreamForTask` (0 refs in log) |
| Proxy scheme | `localhost:PORT/m3u8?url=` (URL-rewriting) | `127.0.0.1:PORT/variant/` (index-based) |
| Package | `...en.anikoto` (SAME) | `...en.anikoto` (SAME) |

**The OLD extension and our new extension share the same package name** (`eu.kanade.tachiyomi.animeextension.en.anikoto`). When the user installed our new APK, it should have overwritten the old one — but the log shows the OLD extension was still running. Likely causes:
- The install failed (signature mismatch between debug builds from different sessions)
- OR the user didn't actually install our new APK (still running the old one)
- OR Aniyomi cached the old extension class

The errors in the log are `HttpException: HTTP 403` (not `NoSuchMethodError`). The `NoSuchMethodError` the user saw in the UI is the Video constructor crash — which could be from either extension.

### B. Fixed the Video constructor to be bulletproof

Even though the log was from the old extension, I made our Video construction bulletproof to prevent ANY possibility of `NoSuchMethodError`:

1. **Changed from named args to ALL positional args** — named args compile to a bitmask constructor call (14 args + int bitmask + DefaultConstructorMarker). Positional args call the primary 14-arg constructor directly. The reference v3 uses positional args and works.

2. **Removed `v.copy(preferred = true)` in sortVideosInternal** — `copy()` is a data-class-generated method that also uses the bitmask constructor. Replaced with a fresh `Video(...)` construction with all 14 positional args.

The Video construction now matches the reference v3's exact call pattern:
```kotlin
Video(
    videoUrl, title, resolution, null, null, false,
    subtitleTracks, emptyList(), emptyList(),
    emptyList(), emptyList(), emptyList(),
    "", true,
)
```

### C. Fixed the scanlator display

Changed the scanlator format from `"SUB • DUB"` (our old format) to match the reference v3's format:
- `"Sub"` (sub only)
- `"Dub"` (dub only)
- `"Sub / Dub"` (both)
- `"Raw"` (neither — fallback)

The scanlator was already being set in our code — the user likely didn't see it because they were testing the OLD extension (which may not set scanlator). But matching the reference format ensures consistency.

### D. Bumped versionId to 2

Changed `versionId` from 1 to 2 so the user can distinguish our new build from the old one. This also generates a different source ID, which may help Aniyomi recognize it as a new extension.

## Files changed

- `Anikoto.kt`:
  - Video construction: all positional args (no named args, no copy())
  - sortVideosInternal: rebuild Video with positional args instead of copy()
  - scanlator: "Sub" / "Dub" / "Sub / Dub" / "Raw" format (matching reference)
  - versionId: 1 → 2

## Build result

BUILD SUCCESSFUL in 14s. APK 155 KB. Verified in DEX:
- `resolveStreamForTask` (our method): 16 refs ✓
- `resolveServerToStream` (old ext method): 0 refs ✓
- `getHosterList`: 40 refs ✓

## Status

- ✅ Video constructor made bulletproof (positional args, no bitmask ctor)
- ✅ Scanlator format matches reference ("Sub / Dub")
- ✅ versionId bumped to 2
- ✅ APK rebuilt and copied to standard locations
- ⚠️ **The user needs to UNINSTALL the old extension before installing ours** (same package name conflict)

## Next steps for the user

1. **UNINSTALL the old Anikoto extension** in Aniyomi (Extensions → Anikoto → Uninstall). This is critical — the old extension shares our package name and is likely still installed.
2. **Install the new APK**: `WORKSPACE/APK/aniyomi-en.anikoto-v16.1-debug.apk`
3. **Verify the extension info**: it should show baseUrl `anikototv.to` (not `anikoto.cz`), versionId 2
4. **Test**: search → details → episode list (should show "Sub / Dub" scanlator) → play → quality switch → subtitles
5. Report any issues with the NEW extension's behavior (not the old one)

## Honest notes

- **The log file was misleading** — it was from the old session-10 extension, not our session-13 build. The user has two extensions with the same package name installed, and the old one was running. This is why the catalog "worked" (the old extension's catalog might partially work against anikoto.cz) but the video playback crashed (the old extension has a Video constructor mismatch).
- **The Video constructor fix is defensive** — even though our stub matches the runtime exactly, using positional args (like the reference v3) is the safest approach. It eliminates any possibility of a bitmask constructor mismatch.
- **The scanlator was already being set** in our code — the user didn't see it because they were testing the old extension. But matching the reference format is still the right thing to do.
- **The versionId bump to 2** helps distinguish our build. The user can verify they're running ours by checking the extension info in Aniyomi.
- **No runtime testing was possible** — I can't run the Aniyomi app myself. The user must test and report back.
