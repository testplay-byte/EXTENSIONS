# Session 15 — Fix Extension Loading Crash + Logo + Version Bump

> Date: 2026-06-23 · Session #: 15 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Fix the critical issue where the extension disappears from Aniyomi/Animiru after trusting it.
Also fix the missing logo (blue placeholder) and bump the version for easy identification.

## What was found — the ROOT CAUSE

### The `ClassNotFoundException` — doubled class name

The user provided a logcat showing the exact crash:
```
ClassNotFoundException: Didn't find class
"eu.kanade.tachiyomi.animeextension.en.anikoto.en.anikoto.Anikoto"
```

The class name was **DOUBLED**: `...anikoto.en.anikoto.Anikoto`. This happened because:

1. Our `build.gradle.kts` had `extClass = ".en.anikoto.Anikoto"`
2. The `applicationIdSuffix = "en.anikoto"` makes the full package name `eu.kanade.tachiyomi.animeextension.en.anikoto`
3. The Aniyomi `AnimeExtensionLoader.loadExtension()` resolves the class name as:
   ```kotlin
   if (sourceClass.startsWith(".")) {
       pkgInfo.packageName + sourceClass  // prepends the FULL package name
   }
   ```
4. So: `eu.kanade.tachiyomi.animeextension.en.anikoto` + `.en.anikoto.Anikoto` = `eu.kanade.tachiyomi.animeextension.en.anikoto.en.anikoto.Anikoto` ← **WRONG (doubled)**

**The fix**: `extClass` should be just `.Anikoto` (the class name relative to the package), NOT `.en.anikoto.Anikoto` (the full path). The loader prepends the full package name automatically.

The reference v3 confirms this: its package is `...en.anikotofinal` and its extClass is `.AnikotoFinal` (just the class name), resolving to `...en.anikotofinal.AnikotoFinal` ✓.

### Why the catalog "worked" before (session 13/14 testing)

In sessions 13-14, the user reported the catalog layer worked (popular/latest/search loaded). But the extension was likely the OLD session-10 build (which had the correct extClass), not our session-13 build. Our session-13 build would have had the same ClassNotFoundException — but the user was testing the old extension, not ours.

## What was fixed

### 1. extClass fix (THE critical fix)
Changed `extClass = ".en.anikoto.Anikoto"` → `extClass = ".Anikoto"` in `build.gradle.kts`.
The loader now resolves to `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` ✓.

### 2. Missing `versionId` meta-data
Added `tachiyomi.animeextension.versionId` meta-data to the manifest (the reference v3 has it, we didn't). Added via `manifestPlaceholders["versionId"] = "2"` in build.gradle.kts.

### 3. Version bump (versionCode 1→2, versionName 16.1→16.2)
- `extVersionCode = 1` → `extVersionCode = 2`
- `versionName = "16.1"` → `versionName = "16.2"`
- APK filename: `aniyomi-en.anikoto-v16.2-debug.apk` (easy to distinguish from old builds)
- Old v16.1 APKs removed from APK folders to avoid confusion

### 4. Proper launcher icons (not blue placeholders)
Generated teal-colored launcher icons with a white play triangle using PIL:
- mdpi 48×48, hdpi 72×72, xhdpi 96×96, xxhdpi 144×144, xxxhdpi 192×192
- Replaces the solid blue placeholder icons from session 08

### 5. Comprehensive logging (from session 14, retained)
- `AnikotoLog.kt` writes to `Download/1118000/anikoto-<timestamp>.log` (rule §6)
- Session header shows `Site: https://anikototv.to`, `versionId=2`, device info
- Detailed logging in getHosterList, resolveStreamForTask, extractors, proxy

## Verification

- **Manifest**: `extClass=".Anikoto"`, `versionId=2`, `nsfw=0` ✓
- **Package**: `eu.kanade.tachiyomi.animeextension.en.anikoto`, versionCode=2, versionName=16.2 ✓
- **DEX**: `animeextension/en/anikoto/Anikoto` (347 refs, correct path), `anikoto/en/anikoto/Anikoto` (0 refs, NO doubling) ✓
- **APK**: 145 KB, `aniyomi-en.anikoto-v16.2-debug.apk`

## Files changed

- `build.gradle.kts`: extClass `.en.anikoto.Anikoto` → `.Anikoto`; versionCode 1→2; added versionId placeholder
- `common/AndroidManifest.xml`: added `tachiyomi.animeextension.versionId` meta-data
- `res/mipmap-*/ic_launcher.png`: 5 new teal+play-triangle icons (replaced blue placeholders)

## The lesson (per rule §2 — document mistakes)

**MISTAKE**: Setting `extClass` to the full relative path (`.en.anikoto.Anikoto`) instead of just the class name (`.Anikoto`).

**WHY IT HAPPENED**: In session 08, the extClass was set to `.en.anikoto.Anikoto` following the pattern `<namespace>/<lang>/<name>/<Class>`. But the Aniyomi loader prepends the FULL package name (including the applicationIdSuffix), so the extClass should only be the class name relative to the package.

**HOW TO AVOID IN FUTURE**: Always check the reference APK's manifest extClass value. The reference v3 uses `.AnikotoFinal` (just the class name), NOT `.en.anikotofinal.AnikotoFinal`. The rule: **extClass = `.ClassName`** (one dot + class name, nothing more).

**DOCUMENTED** in `MEMORY/issues-resolutions/` for future reference.

## Status

- ✅ ClassNotFoundException FIXED — extClass is now `.Anikoto` (not doubled)
- ✅ versionId meta-data added to manifest
- ✅ versionCode bumped to 2, versionName to 16.2
- ✅ Proper launcher icons (teal + play triangle, not blue placeholder)
- ✅ APK built and verified: `aniyomi-en.anikoto-v16.2-debug.apk` (145 KB)
- ✅ Old v16.1 APKs removed

## Next steps

1. **UNINSTALL the old Anikoto extension** in Aniyomi/Animiru first
2. **Install the new APK**: `WORKSPACE/APK/aniyomi-en.anikoto-v16.2-debug.apk`
3. **Trust the extension** — it should now STAY in the list (not disappear)
4. **Test**: search → details → episode list → play → quality switch → subtitles
5. Send the log file from `Download/1118000/` if any issues remain

## Honest notes

- **This was a fundamental build-config mistake** that should have been caught in session 08. The extClass value was wrong from the very first build. It only "worked" in earlier testing because the user was testing the OLD extension, not ours.
- **The logcat was the key** — the `ClassNotFoundException` with the doubled class name immediately pointed to the extClass issue. Without the logcat, I would have been guessing.
- **I should have verified the merged manifest's extClass against the reference v3's manifest** in session 08 when I first set up the build config. This is now a checklist item for future extensions.
- **The versionId meta-data was also missing** — the reference v3 has it, we didn't. While the loader doesn't strictly require it (it reads versionId from the class at runtime), having it in the manifest is best practice and matches the reference.
- **The logo was a known issue** from session 08 (solid blue placeholders). Now replaced with proper teal+play icons.
