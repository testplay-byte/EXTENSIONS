# Build Checklist — Anikoto Extension

> Last updated: 2027-06-27 (session 50) · Status: MANDATORY
> Read this BEFORE every build. Every item must be ✓.

This checklist was created after sessions 13-16, where 3 critical build-config mistakes
caused the extension to crash on load. Following this checklist prevents ALL known issues.

## Pre-build checklist

### 1. extClass is correct
- [ ] `extClass = "eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto"` (FULL path, no leading dot)
- [ ] **Why** (★ updated session 49): the Aniyomi loader (`AnimeExtensionLoader.kt:297-301`) does:
      - if `extClass` starts with `.` → `packageName + extClass` (relative)
      - else → use `extClass` as-is (absolute)
      Our `applicationId` is `...anikoto180` (since v16.9) but the source code package is
      `...anikoto` (unchanged — no need to move files). Using a relative `.Anikoto` would make
      the loader look for `...anikoto180.Anikoto` which doesn't exist → ClassNotFoundException.
      The full path makes the loader use it as-is → finds the class at `...anikoto.Anikoto`.
      **Before v16.9** (when applicationId was `...anikoto`): `extClass = ".Anikoto"` worked
      because `packageName + ".Anikoto"` = `...anikoto.Anikoto` matched the source package.

### 2. Stubs are in the `:stubs` module (NOT in the extension src)
- [ ] `stubs/src/main/kotlin/` contains the 26 stub files
- [ ] `src/en/anikoto/src/main/kotlin/eu/kanade/tachiyomi/animeextension/` is the ONLY tachiyomi subdir in the extension
- [ ] `compileOnly(project(":stubs"))` in `src/en/anikoto/build.gradle.kts`
- [ ] **Why**: if stubs are in the extension's DEX, `ChildFirstPathClassLoader` loads them → `Exception("Stub!")` crash

### 3. Version is bumped (versionCode) + versionId is STABLE
- [ ] `extVersionCode` incremented in `build.gradle.kts` (per-build: 12, 13, 14, ...)
- [ ] `extVersionId` = STABLE (do NOT bump with versionCode). Currently 11.
      The source `id = MD5("anikoto/en/$extVersionId")`. Bumping versionId orphans saved anime.
      Only change versionId if the site's URL structure breaks (domain change).
- [ ] `override val versionId` in `Anikoto.kt` = STABLE (matches extVersionId, currently 11)
- [ ] `EXTENSION_VERSION` in `AnikotoLog.kt` updated with the new versionCode (e.g. "v16.12 (...)")
- [ ] **Why**: `versionCode` tells Android "this is a newer APK" (update signal).
      `versionId` tells the app "this is the same source" (saved-anime link). They are SEPARATE.
      See `EXTENSIONS/anikoto/MEMORY/sites/getsources-migration-and-id-analysis.md` §2.

### 4. Manifest is correct
- [ ] `common/AndroidManifest.xml` has `tachiyomi.animeextension.class` = `${extClass}`
- [ ] `common/AndroidManifest.xml` has `tachiyomi.animeextension.nsfw` = `${nsfw}`
- [ ] `common/AndroidManifest.xml` has `tachiyomi.animeextension.versionId` = `${versionId}`
- [ ] `common/AndroidManifest.xml` has `android:usesCleartextTraffic="true"` (for localhost proxy)
- [ ] `common/AndroidManifest.xml` does NOT have `WRITE_EXTERNAL_STORAGE` (removed session 46 — logcat-only logging now)

### 5. Gradle config is correct
- [ ] `settings.gradle.kts` includes `:stubs` and `:src:en:anikoto`
- [ ] `build.gradle.kts` (root) declares `android.library` plugin `apply false`
- [ ] `stubs/build.gradle.kts` uses `android.library` plugin, all deps `compileOnly`

## Post-build verification

After `./gradlew :src:en:anikoto:assembleDebug`:

### 6. APK exists and has the right version
```bash
source /home/z/my-project/.android-env.sh
APK=src/en/anikoto/build/outputs/apk/debug/aniyomi-en.anikoto-v16.*-debug.apk
ls -la $APK
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump badging $APK | grep '^package:'
```
- [ ] APK filename has the correct version (e.g., `v16.3`)
- [ ] `versionCode` and `versionName` match the build config
- [ ] `package: name='eu.kanade.tachiyomi.animeextension.en.anikoto180'` (★ changed in session 49)

### 7. extClass is NOT doubled
```bash
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump xmltree --file AndroidManifest.xml $APK | grep -A1 'animeextension\.class'
```
- [ ] Value is `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` (★ FULL path since session 49 — no leading dot)

### 8. Stubs are NOT in the DEX (CRITICAL)
```bash
# Should be 0 (no "Stub!" string):
$ANDROID_HOME/build-tools/34.0.0/dexdump -l plain $APK 2>/dev/null | grep -c 'Stub!'

# Should be 0 (no stub class DEFINITIONS):
$ANDROID_HOME/build-tools/34.0.0/dexdump $APK 2>/dev/null | grep -c 'Class descriptor.*AnimeHttpSource;'
$ANDROID_HOME/build-tools/34.0.0/dexdump $APK 2>/dev/null | grep -c 'Class descriptor.*Leu/kanade/tachiyomi/network/NetworkHelper;'
```
- [ ] "Stub!" count = 0
- [ ] AnimeHttpSource definitions = 0
- [ ] NetworkHelper definitions = 0

### 9. Extension classes ARE in the DEX
```bash
$ANDROID_HOME/build-tools/34.0.0/dexdump -l plain $APK 2>/dev/null | grep -c 'Leu/kanade/tachiyomi/animeextension/en/anikoto/Anikoto;'
```
- [ ] Anikoto > 0 (class present)

### 10. Icons are present
```bash
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump resources $APK | grep 'ic_launcher'
```
- [ ] 5 densities: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi

### 11. Copy + clean up
- [ ] Copy APK to `EXTENSIONS/anikoto/APK/`
- [ ] Copy APK to `EXTENSIONS/anikoto/APK/`
- [ ] Delete old version APKs from both folders
- [ ] Record the MD5: `md5sum $APK`

## Pre-install checklist (for the user)

- [ ] Uninstall the old Anikoto extension in Aniyomi/Animiru FIRST (same package name conflict)
- [ ] Verify the APK filename has the latest version
- [ ] Verify the MD5 matches the recorded value

## Known issues (not bugs — expected behavior)

- `Unsupported class loader: ChildFirstPathClassLoader` warning — this is app-side, NOT our bug. All Aniyomi extensions trigger it.
- `OplusScrollToTopManager: Receiver not registered` — this is an Oppo/OnePlus UI crash-cleanup side effect, NOT our bug. Only happens when the app crashes for another reason.
- `InputManager-JNI: Input channel disposed` — same as above, a crash-cleanup side effect.

## The 3 critical mistakes (DO NOT REPEAT)

1. **extClass format**: Since session 49, extClass is the FULL path `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` (no leading dot). This is because applicationId (`...anikoto180`) ≠ source package (`...anikoto`). A relative `.Anikoto` would resolve to `...anikoto180.Anikoto` which doesn't exist.
2. **Stubs in APK**: stubs must be `compileOnly` in a separate `:stubs` module. Never in the extension's `src/main/kotlin/`.
3. **No version bump**: always bump `versionCode` on every rebuild. `versionId` stays STABLE at 11. Delete old APKs.
