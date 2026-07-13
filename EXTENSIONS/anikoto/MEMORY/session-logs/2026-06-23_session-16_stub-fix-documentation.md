# Session 16 — Fix Stub! Crash + Document All Build Issues

> Date: 2026-06-23 · Session #: 16 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Fix the `Exception("Stub!")` crash that prevented the extension from loading after trusting,
and comprehensively document all build issues encountered in sessions 13-16 so they're never
repeated.

## What was done

### A. Diagnosed the Stub! crash

The user's logcat (after installing v16.2 and trusting it) showed:
```
Caused by: java.lang.Exception: Stub!
    at eu.kanade.tachiyomi.animesource.online.AnimeHttpSource.<init>(AnimeHttpSource.kt:21)
    at eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto.<init>(Anikoto.kt:42)
```

The extClass fix (session 15) worked — the class name was no longer doubled. But now the
`AnimeHttpSource` constructor threw `Exception("Stub!")`. Root cause: the ext-lib v16 stub
source files were compiled INTO the extension's APK, and Aniyomi's `ChildFirstPathClassLoader`
(child-first!) loaded the stubs from the extension's DEX before the app's real classes.

### B. Fixed by creating a separate `:stubs` module

1. Created `stubs/` directory with `build.gradle.kts` (android.library plugin, all deps compileOnly)
2. Moved all 26 stub files from `src/en/anikoto/src/main/kotlin/eu/kanade/tachiyomi/{animesource,network,util,AppInfo.kt}` to `stubs/src/main/kotlin/`
3. Only `animeextension/` remains in the extension's src
4. Added `compileOnly(project(":stubs"))` to the extension's dependencies
5. Added `alias(libs.plugins.android.library) apply false` to root build.gradle.kts
6. Added `include(":stubs")` to settings.gradle.kts

### C. Verified the fix

- **"Stub!" string in DEX**: 0 ✓ (was present before)
- **AnimeHttpSource class DEFINITION**: 0 ✓ (stub NOT in APK)
- **NetworkHelper class DEFINITION**: 0 ✓
- **Anikoto class DEFINITION**: 1 ✓ (our class IS in APK)
- **APK size**: 102 KB (down from 147 KB — 45 KB of stub DEX removed)
- **Runtime**: extension loads and stays in the list after trusting ✓ (user confirmed)

### D. Bumped version to 16.3

- versionCode: 2 → 3
- versionName: 16.2 → 16.3
- versionId: 2 → 3 (in build.gradle.kts, Anikoto.kt, AnikotoLog.kt)
- APK: `aniyomi-en.anikoto-v16.3-debug.apk`
- MD5: `be6b3f41e979d1733b63218f3a378974`

### E. Documented all build issues comprehensively

Created 4 documentation files:

1. **`MEMORY/issues-resolutions/01-extclass-doubling.md`** — the ClassNotFoundException issue (extClass doubling)
2. **`MEMORY/issues-resolutions/02-stub-crash.md`** — the Stub! crash (stubs compiled into APK)
3. **`MEMORY/issues-resolutions/03-versionid-logo-bumping.md`** — missing versionId, placeholder logo, version bump practice
4. **`MEMORY/guides/04-build-checklist.md`** — ★ the MANDATORY pre/post-build checklist with 11 verification items

Updated:
- `MEMORY/issues-resolutions/README.md` — added entries 01-03
- `MEMORY/guides/README.md` — added entry 04

## Files created

**Build system:**
- `stubs/build.gradle.kts` — the stubs module build config
- `stubs/src/main/kotlin/eu/kanade/tachiyomi/` — 26 stub files moved here

**Documentation:**
- `MEMORY/issues-resolutions/01-extclass-doubling.md`
- `MEMORY/issues-resolutions/02-stub-crash.md`
- `MEMORY/issues-resolutions/03-versionid-logo-bumping.md`
- `MEMORY/guides/04-build-checklist.md`
- `MEMORY/session-logs/2026-06-23_session-16_stub-fix-documentation.md` (this log)

## Files changed

- `build.gradle.kts` (root) — added `android.library` plugin
- `settings.gradle.kts` — added `include(":stubs")`
- `src/en/anikoto/build.gradle.kts` — added `compileOnly(project(":stubs"))`, bumped version
- `src/en/anikoto/src/main/kotlin/.../Anikoto.kt` — versionId 2→3
- `src/en/anikoto/src/main/kotlin/.../AnikotoLog.kt` — EXTENSION_VERSION versionId=3
- `MEMORY/issues-resolutions/README.md` — added entries 01-03
- `MEMORY/guides/README.md` — added entry 04

## Status at end of session

- ✅ Extension loads successfully in Aniyomi/Animiru (user confirmed: "finally the issue has been resolved")
- ✅ All 3 critical build issues documented with root cause, fix, verification, and prevention
- ✅ Build checklist created (11 items) to prevent repeating these mistakes
- ✅ Version bumped to 16.3 with proper MD5 recording
- ✅ Stubs properly isolated in `:stubs` module (compileOnly, NOT in APK)
- ⏳ User is testing the extension's functionality (search, details, episodes, playback)

## The 3 critical mistakes (now documented + checklist-protected)

1. **extClass doubling** (session 15): `extClass` was `.en.anikoto.Anikoto` but should be `.Anikoto`. The loader prepends the full packageName.
2. **Stubs in APK** (session 16): stubs were compiled INTO the APK. `ChildFirstPathClassLoader` loaded them → `Exception("Stub!")`. Fixed by moving to `:stubs` module with `compileOnly`.
3. **No version bump** (sessions 13-16): kept versionCode=1 across iterations. Now we bump every rebuild and delete old APKs.

## Next steps

The user is testing the extension. We wait for their feedback on:
- Search functionality
- Anime details loading
- Episode list (with sub/dub scanlator display)
- Video playback (all servers, audio types, resolutions)
- Subtitles
- Quality switching

If issues arise, the comprehensive logging (AnikotoLog → `Download/1118000/`) will help diagnose them quickly.

## Honest notes

- **The Stub! crash was the hardest issue** — it required understanding the `ChildFirstPathClassLoader` behavior and the compile-time-vs-runtime discrepancy. The root cause was documented in `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` back in session 03, but we didn't act on it until the crash happened. **Lesson: always act on documented risks before they become runtime crashes.**
- **The build checklist is the most important output of this session** — it turns 3 sessions of painful debugging into a 2-minute pre/post-build verification. Future builds should NEVER repeat these mistakes if the checklist is followed.
- **The user was patient through 4 sessions of build issues** (13, 14, 15, 16). The root causes were all build-config mistakes from session 08 that only surfaced at runtime. This highlights the importance of testing the build on a device EARLY, not waiting until the full feature is implemented.
