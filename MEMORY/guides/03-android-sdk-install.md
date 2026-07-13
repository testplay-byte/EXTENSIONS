# Guide: Android SDK Install (verified procedure)

> Last updated: 2026-06-22 · Status: VERIFIED (installed & functional this session)
> Install location: `/home/z/my-project/ANDROID_SDK` (458 MB total)

This is the **verified, tested** procedure for installing the Android SDK in this environment.
Follow it exactly if you ever need to reinstall (e.g., after a clean wipe, or on a new machine).

---

## 0. Current install status (verified 2026-06-22, session 05)

- **Location:** `/home/z/my-project/ANDROID_SDK`
- **Components installed:**
  - `cmdline-tools/latest` (sdkmanager v12.0) — 148 MB
  - `platform-tools` v37.0.0 (adb v1.0.41, fastboot, etc.) — 22 MB
  - `platforms;android-34` v3 (android.jar, data, skins, etc.) — 138 MB
  - `build-tools;34.0.0` (aapt, aapt2, d8, apksigner, etc.) — 151 MB
- **Licenses:** all 7 accepted (in `$ANDROID_HOME/licenses/`)
- **Env file:** `/home/z/my-project/.android-env.sh` (sourceable: `source /home/z/my-project/.android-env.sh`)
- **`local.properties`:** OPTIONAL. The build uses `ANDROID_HOME` from the env script when absent. If you want one, create it at `EXTENSIONS/<name>/DEV/local.properties` with `sdk.dir=/home/z/my-project/ANDROID_SDK`. AniKoto builds without one.
- **✅ FUNCTIONAL:** `adb version`, `aapt2 version`, `sdkmanager --list_installed` all work.

## 0.1 ⚠️ KNOWN BLOCKER: `javac` missing (needed for Gradle BUILD, not for SDK)

The environment has **OpenJDK 21.0.11 JRE** (no `javac`), and there's **no passwordless sudo** to
apt-install the JDK. `sdkmanager` runs fine on the JRE (verified), but **Gradle builds need `javac`**.

- `openjdk-21-jdk-headless` IS in the apt repos (candidate `21.0.11+10-1~deb13u2`) — same version as the installed JRE.
- To install it, the user must either: (a) grant passwordless sudo, (b) install it themselves, OR (c) provide a JDK tarball (e.g., Adoptium/Temurin JDK 17 or 21) in `upload/` that I can extract locally + add to PATH.
- JDK 17 or 21 both satisfy the ext-lib v16 requirement (Java 17 bytecode; 21 reads 17 fine).
- **This blocker does NOT prevent SDK use** (sdkmanager, adb, aapt2 all work on the JRE). It only blocks `./gradlew assembleDebug` until resolved.

---

## 1. The exact install sequence (tested, handles all gotchas)

### Step 1 — Set environment + create persistent env file

```bash
export ANDROID_HOME=/home/z/my-project/ANDROID_SDK
export ANDROID_SDK_ROOT=/home/z/my-project/ANDROID_SDK
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Persist to a sourceable file:
cat > /home/z/my-project/.android-env.sh << 'EOF'
export ANDROID_HOME=/home/z/my-project/ANDROID_SDK
export ANDROID_SDK_ROOT=/home/z/my-project/ANDROID_SDK
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
```
> Each new shell must `source /home/z/my-project/.android-env.sh` before running sdkmanager/adb.
> (Bash tool calls here don't persist env across invocations, so always source it first.)

### Step 2 — Download command-line tools

```bash
mkdir -p $ANDROID_HOME/cmdline-tools
cd /tmp
wget -c "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
# ~150 MB. -c makes it resumable. Verify integrity:
unzip -t commandlinetools-linux-11076708_latest.zip
```

### Step 3 — Unzip + CRITICAL rename

```bash
unzip -q /tmp/commandlinetools-linux-11076708_latest.zip -d $ANDROID_HOME/cmdline-tools
# The zip extracts to a folder named "cmdline-tools". It MUST be renamed to "latest":
mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
# Final path must be: $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
ls $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager   # verify
```
> **CRITICAL gotcha:** if you skip the rename, `sdkmanager` runs but can't find its own JARs (it
> expects to be at `cmdline-tools/latest/bin/sdkmanager`, not `cmdline-tools/cmdline-tools/bin/...`).

### Step 4 — Accept all licenses non-interactively

```bash
source /home/z/my-project/.android-env.sh
yes | sdkmanager --licenses
# Verify: ls $ANDROID_HOME/licenses/ should show 7 .xml-ish files (android-sdk-license, etc.)
```

### Step 5 — Install packages

```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```
> **GOTCHA:** sdkmanager output may appear empty on success (progress bars use `\r`). **Verify by
> checking directories, not logs.** The install log may be empty even on full success.
>
> **GOTCHA:** if interrupted, clean leftovers before retry:
> ```bash
> rm -rf $ANDROID_HOME/.temp $ANDROID_HOME/build-tools/34.0.0 $ANDROID_HOME/platforms/android-34
> ```

### Step 6 — Verify the 3 key files exist + tools run

```bash
ls $ANDROID_HOME/platform-tools/adb                      # must exist
ls $ANDROID_HOME/platforms/android-34/android.jar        # must exist
ls $ANDROID_HOME/build-tools/34.0.0/aapt2                # must exist

# Functional checks:
$ANDROID_HOME/platform-tools/adb version                 # adb v1.0.41
$ANDROID_HOME/build-tools/34.0.0/aapt2 version           # aapt2 v2.x
sdkmanager --list_installed                              # lists 3 packages
```

### Step 7 — Create `local.properties` in each Gradle project root

```bash
cat > <PROJECT_ROOT>/local.properties << EOF
sdk.dir=/home/z/my-project/ANDROID_SDK
EOF
```
- One per extension's `DEV/` folder (at `EXTENSIONS/<name>/DEV/local.properties`).
- **Do NOT commit `local.properties` to git** (machine-specific). Add to `.gitignore`.
- A ready-to-copy one exists in any built extension, e.g. `EXTENSIONS/anikoto/DEV/` (though AniKoto itself relies on the env script and has no local.properties).

---

## 2. Disk usage (verified)

```
148M  cmdline-tools
 22M  platform-tools
138M  platforms/android-34
151M  build-tools/34.0.0
  7M  licenses + .knownPackages + misc
458M  total  (vs ~800MB estimate — minimal set, no system-images/emulator)
```

## 3. Reinstall / cleanup procedure (if ever needed)

```bash
# Full clean wipe:
rm -rf /home/z/my-project/ANDROID_SDK
# Then re-run steps 1-7 above.
```

## 4. What is NOT installed (intentionally — to save space)

- ❌ `system-images;android-34;*` (emulator system images — ~1GB each, not needed; we test on a real device via adb)
- ❌ `emulator` package (~500MB, not needed; same reason)
- ❌ `ndk-bundle` (~1GB, not needed unless an extension uses native code — none of yuzono's do)
- ❌ `sources;android-34` (source bundles for IDE browsing — not needed for builds)
- ❌ Extra build-tools versions (only 34.0.0; add others if a lib needs them)

If a future extension needs any of these, install with:
```bash
sdkmanager "<package>;<path>"
```

## 5. Related docs

- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` — the Gradle build config (now has SDK as ✓ prerequisite, JDK as ⚠️ blocker).
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` §8 — why JDK 17+ (not 11) for ext-lib v16.
- `EXTENSIONS/_template/README.md` — the per-extension scaffold (scaffold the Gradle project here when starting a new extension).
- `MEMORY/PROJECT_RULES.md` §5 (don't mess up the environment — this install respects that).
