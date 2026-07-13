# Session 25 — Restore Environment + Verify Build (v16.11)

> Date: 2026-06-24 · Session #: 25 · Duration: ~short · Timezone: America/Los_Angeles

## Goal

Restore the ANIKOTO Aniyomi extension project from the `7.zip` backup into
`/home/z/my-project/`, reinstall the build toolchain (JDK 17 + Android SDK, which
don't survive backup), and verify the v16.11 extension still builds cleanly in the
restored environment. No code changes — this is a pure restore + build-verification
session.

## What was done

### A. Inspected the backup before trusting it

- Verified `/home/z/my-project/upload/7.zip` is a real Zip archive (~23 MB, 10,162 files).
- Listed the full contents (`unzip -l`) — confirmed it matches `RESTORE.md`'s description:
  `MEMORY/`, `WORKSPACE/`, `REFERENCE_HUB/`, `APK/REFERENCE/`, top-level docs.
- Confirmed **no path conflicts** with the existing Next.js project (`src/`, `node_modules/`,
  `package.json`, etc.) — the backup's top-level entries are all distinct.
- Extracted `RESTORE.md`, `STARTUP_PROMPT.md`, `PROJECT_INDEX.md`, `MEMORY/README.md`,
  `MEMORY/PROJECT_RULES.md`, `MEMORY/session-logs/README.md` to a temp dir and **read the
  intended restore procedure first**, before extracting anything else.

### B. Full extraction into `/home/z/my-project/`

- `unzip -q -o upload/7.zip` → restored `MEMORY/`, `WORKSPACE/`, `REFERENCE_HUB/`, `APK/`,
  `.android-env.sh`, `worklog.md` (97 KB — previous session history), `RESTORE.md`,
  `STARTUP_PROMPT.md`, `PROJECT_INDEX.md`.
- Next.js project files untouched (no overwrites of `src/`, `package.json`, etc.).
- The zip deliberately excludes `JDK`/`ANDROID_SDK`/`build/`/`.gradle/` (per `RESTORE.md`
  §"What's NOT in this zip") — these are recreated below.

### C. Reinstalled JDK 17 (Temurin 17.0.13+11)

Per `RESTORE.md` Step 2 + `.android-env.sh` expectations:

- Downloaded from `https://api.adoptium.net/v3/binary/version/jdk-17.0.13+11/linux/x64/jdk/hotspot/normal/eclipse` (182 MB).
- Extracted to `/home/z/my-project/.jdk/jdk-17.0.13+11/` (matches `JAVA_HOME` in `.android-env.sh`).
- Verified: `javac 17.0.13`, `openjdk version "17.0.13"`.

### D. Reinstalled Android SDK (per `MEMORY/guides/03-android-sdk-install.md`)

Followed the verified procedure exactly:

- Downloaded `commandlinetools-linux-11076708_latest.zip` (150 MB), integrity-checked with `unzip -t`.
- Unzipped to `$ANDROID_HOME/cmdline-tools` + **critical rename** `cmdline-tools/cmdline-tools` → `cmdline-tools/latest`.
- `sdkmanager --version` → 12.0.
- `yes | sdkmanager --licenses` → all 7 licenses accepted.
- `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"` → installed (output empty on success, as the guide's gotcha notes — verified by checking directories, not logs).
- Verified: `platform-tools` v37.0.0, `platforms;android-34` v3, `build-tools;34.0.0`; `adb v1.0.41`; `aapt2` present at full path.
- Total disk: 458 MB (exactly matches the guide's documented size).

### E. Verified the mandatory pre-build checklist (`MEMORY/guides/04-build-checklist.md`)

All 5 pre-build items ✓ before building:

1. **extClass** = `.Anikoto` in `src/en/anikoto/build.gradle.kts` (NOT doubled) ✓
2. **Stubs in `:stubs` module**: 27 stub `.kt` files in `stubs/src/main/kotlin/`; only `animeextension/` in the extension's src (no stub leak); `compileOnly(project(":stubs"))` present ✓
3. **Version consistent**: `extVersionCode=11`, `extVersionId=11` (build.gradle.kts); `override val versionId = 11` (Anikoto.kt:48); `EXTENSION_VERSION = "v16 (ext-lib 16, versionId=11)"` (AnikotoLog.kt:30) ✓
4. **Manifest** (`common/AndroidManifest.xml`): `class=${extClass}`, `nsfw=${nsfw}`, `versionId=${versionId}`, `usesCleartextTraffic="true"`, `WRITE_EXTERNAL_STORAGE` ✓
5. **Gradle config**: `settings.gradle.kts` includes `:stubs` + `:src:en:anikoto`; root `build.gradle.kts` declares `android.library apply false`; `stubs/build.gradle.kts` uses `android.library` with all deps `compileOnly` ✓

### F. Built the extension

```
source /home/z/my-project/.android-env.sh
cd /home/z/my-project/WORKSPACE/DEV/ANIKOTO/DEVELOPMENT_CODE
./gradlew :src:en:anikoto:assembleDebug --no-daemon --console=plain
```

→ **BUILD SUCCESSFUL in 1m 37s** (44 actionable tasks: 42 executed, 2 from cache). First-run
downloaded Gradle + dependencies; configuration cache entry stored.

### G. Verified the mandatory post-build checklist (items 6-11)

All passed:

- **[6] APK + version**: `src/en/anikoto/build/outputs/apk/debug/aniyomi-en.anikoto-v16.11-debug.apk` (105,848 bytes). `aapt2 dump badging` → `package: name='eu.kanade.tachiyomi.animeextension.en.anikoto' versionCode='11' versionName='16.11'` ✓
- **[7] extClass not doubled**: manifest value = `.Anikoto` (NOT `.en.anikoto.Anikoto`) ✓
- **[8] CRITICAL — stubs NOT in DEX**: `Stub!` count = **0**; `AnimeHttpSource` class defs = **0**; `NetworkHelper` class defs = **0** ✓
- **[9] Extension class IS in DEX**: `Anikoto` references = **145** (> 0) ✓
- **[10] Icons**: all 5 densities present (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) ✓
- **[11] Copy + MD5**: copied freshly built APK to `WORKSPACE/DEV/ANIKOTO/APK/` and `WORKSPACE/APK/` (replacing the backup's v16.11 copies). Recorded MD5: `d3ea83903466bd418228bf0c363ff6e1`.

### H. MD5 reproducibility note

The freshly built APK is **identical in size** (105,848 bytes) to the backup's v16.11 APK, but
the **MD5 differs** (`d3ea83903466bd418228bf0c363ff6e1` new vs `2e5ac59c8b9c3eccb8153b7bbab242f2`
backup). This is **expected and normal**: APKs are not byte-reproducible by default — the debug
signing keystore was regenerated (the `.gradle/` cache + keystore weren't in the backup), and
build timestamps differ. All functional verification checks (version, extClass, no stubs, classes
present, icons) pass, confirming the build is correct. The APK is functionally equivalent to the
one the user was testing.

## Files created

- `MEMORY/session-logs/2026-06-24_session-25_restore-and-rebuild.md` (this log)

## Files changed

- `/home/z/my-project/WORKSPACE/DEV/ANIKOTO/APK/aniyomi-en.anikoto-v16.11-debug.apk` — replaced with freshly built copy
- `/home/z/my-project/WORKSPACE/APK/aniyomi-en.anikoto-v16.11-debug.apk` — replaced with freshly built copy
- `/home/z/my-project/worklog.md` — appended session-25 restore record

## Environment installed (not in backup, recreated)

- `/home/z/my-project/.jdk/jdk-17.0.13+11/` — Temurin JDK 17.0.13+11 (182 MB extracted)
- `/home/z/my-project/ANDROID_SDK/` — Android SDK (458 MB: cmdline-tools 12.0, platform-tools v37, android-34, build-tools 34.0.0)

## Status at end of session

- ✅ Backup fully extracted into `/home/z/my-project/` (MEMORY/, WORKSPACE/, REFERENCE_HUB/, APK/, top-level docs)
- ✅ JDK 17.0.13+11 reinstalled and verified (`javac 17.0.13`)
- ✅ Android SDK reinstalled and verified (458 MB, all 3 packages functional)
- ✅ Pre-build checklist: all 5 items ✓
- ✅ Build: `BUILD SUCCESSFUL in 1m 37s` — v16.11 APK produced (105,848 bytes)
- ✅ Post-build checklist: all 6 items ✓ (version, extClass, **Stub! count = 0**, classes present, icons, MD5 recorded)
- ✅ APK copied to both `WORKSPACE/DEV/ANIKOTO/APK/` and `WORKSPACE/APK/`
- ✅ Next.js project (the Z.ai Code interface on port 3000) untouched and still serving `/` with 200s

## Current project state (unchanged from session 24 / v16.11)

The extension is **working** — installs, loads, plays videos. Catalog + video extraction fully
implemented. Pending items (waiting on user test feedback from v16.11):

- **Audio switching**: may lose audio when switching quality/server. v16.11 has `initialized=false`
  + `resolveVideo` logging to diagnose.
- **Subtitles**: fixed in v16.11 (track lang uses label "English" instead of ISO "eng"). Needs user confirmation.
- **Filters**: minor issues noted by user — skipped for now.

## Next steps

Await user direction. Likely follow-ups:
- If the user reports test results from v16.11: read the log from `Download/1118000/` and diagnose
  using `AnikotoLog`. Follow rule §2 (one change at a time).
- If the user wants a new feature/fix: bump `versionCode`/`versionId`/`versionName`, delete old APKs,
  run the full build checklist, record the new MD5.

## Honest notes

- The restore was **clean and the build succeeded on the first try** — a testament to how well
  documented the build procedure is (`RESTORE.md`, `guides/03`, `guides/04`). Following the
  verified SDK install guide and the build checklist exactly avoided all 3 of the previously-documented
  build issues.
- The MD5 difference between the new and backup APKs is expected (debug keystore regenerated) and
  does NOT indicate a problem. If byte-reproducible builds become important, we'd need to configure
  `apksigner` with a fixed debug keystore + reproducible build flags — out of scope for this restore.
- The `worklog.md` (last entry session-18, v16.5) and the session-logs folder (last entry session-16)
  are **out of sync** with STARTUP_PROMPT's "session 24, v16.11" framing — sessions 17-24 produced
  v16.6→v16.11 but weren't fully logged to both places. This restore session (25) is now logged to both.
