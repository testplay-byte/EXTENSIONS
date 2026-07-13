# Session 05 — Android SDK Install

> Date: 2026-06-22 · Session #: 05 · Duration: ~short · Timezone: America/Los_Angeles

## Goal

Install the Android SDK to `/home/z/my-project/ANDROID_SDK` following the user's exact tested
sequence (6 steps + verify). Handle it properly, document, and flag any issues.

## What was done

Followed the user's exact sequence, verifying each step:

1. **Env setup + persistent file** — created `/home/z/my-project/ANDROID_SDK/` +
   `/home/z/my-project/.android-env.sh` (sourceable; sets `ANDROID_HOME`, `ANDROID_SDK_ROOT`, PATH).
2. **Download** — `wget -c` the `commandlinetools-linux-11076708_latest.zip` (153.6 MB) to `/tmp`.
   Integrity verified with `unzip -t`. Confirmed the zip extracts to a top-level `cmdline-tools/`
   folder (the rename gotcha is real).
3. **Unzip + CRITICAL rename** — `unzip -q` into `$ANDROID_HOME/cmdline-tools/`, then
   `mv cmdline-tools/cmdline-tools cmdline-tools/latest`. Verified `latest/bin/sdkmanager` exists.
4. **Licenses** — `yes | sdkmanager --licenses`. All 7 license files written to `$ANDROID_HOME/licenses/`.
5. **Install packages** — `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`.
   Per the gotcha, output appeared empty (progress bars use `\r`) — **verified by checking directories**.
6. **Verify** — all 3 key files exist + functional:
   - `platform-tools/adb` (11 MB) — `adb version` → v1.0.41 (37.0.0) ✓
   - `platforms/android-34/android.jar` (26 MB) ✓
   - `build-tools/34.0.0/aapt2` (6 MB) — `aapt2 version` → v2.19-10229193 ✓
   - `sdkmanager --list_installed` confirms all 3 packages ✓
7. **`local.properties`** — created at `WORKSPACE/DEV/_TEMPLATE/DEVELOPMENT_CODE/local.properties`
   with `sdk.dir=/home/z/my-project/ANDROID_SDK`. Ready to copy into each new extension.

**Total SDK size:** 458 MB (less than the ~800MB estimate — minimal set, no emulator/system-images/NDK).

### ⚠️ Blocker found + handled: `javac` missing

- The environment has **OpenJDK 21.0.11 JRE** only (no `javac`), and **no passwordless sudo** to
  apt-install the JDK.
- `openjdk-21-jdk-headless` IS in apt repos (candidate `21.0.11+10-1~deb13u2`) — same version as the
  installed JRE.
- **Key insight (verified):** `sdkmanager` runs on the JRE (`java`), NOT `javac`. So the SDK install
  succeeded fully without the JDK. `javac` is only needed at **Gradle build time** (compiling the
  extension).
- Documented as a build-time blocker in `MEMORY/guides/03-android-sdk-install.md` §0.1 +
  `MEMORY/guides/01-build-setup-for-ext-lib-16.md` §0. NOT a blocker for SDK use.

### Documentation written

- `MEMORY/guides/03-android-sdk-install.md` — ★ the verified install procedure (all 6 steps +
  gotchas + disk usage + cleanup + what's NOT installed + the JDK blocker). Reusable for reinstall.
- Updated `MEMORY/guides/01-build-setup-for-ext-lib-16.md` §0 (prerequisites: SDK ✓ installed, JDK ⚠️ blocker).
- Updated `MEMORY/guides/README.md` (added guide 03).
- Updated `WORKSPACE/DEV/_TEMPLATE/README.md` (noted `local.properties` is now pre-created).
- Updated `MEMORY/README.md` §7 (quick-links: added guide 03, bumped latest-session pointer).

## Key findings / decisions

1. **SDK fully installed and functional** at `/home/z/my-project/ANDROID_SDK` (458 MB).
2. **The critical `cmdline-tools → latest` rename gotcha is real** — confirmed the zip extracts to
   `cmdline-tools/cmdline-tools/`, which must become `cmdline-tools/latest/`. Verified post-rename.
3. **sdkmanager runs on JRE alone** — `javac` not needed for SDK install/management, only for Gradle
   builds. This let the install proceed despite the missing JDK.
4. **The "empty output" gotcha is real** — sdkmanager's progress bars use `\r`, so the install log
   looks empty on success. Directory verification is the reliable check (per the user's guidance).
5. **`javac` is a build-time blocker** — the user must provide a JDK before we can `./gradlew
   assembleDebug`. Options documented: (a) grant sudo, (b) self-install, (c) JDK tarball in `upload/`.
6. **Persistent env file** at `/home/z/my-project/.android-env.sh` — every new shell must `source` it
   before running `adb`/`sdkmanager`/`./gradlew` (Bash tool calls don't persist env across invocations).

## Files created / modified

New (3):
- `/home/z/my-project/ANDROID_SDK/` — the SDK itself (458 MB: cmdline-tools, platform-tools, platforms/android-34, build-tools/34.0.0, licenses)
- `/home/z/my-project/.android-env.sh` — sourceable env file
- `/home/z/my-project/WORKSPACE/DEV/_TEMPLATE/DEVELOPMENT_CODE/local.properties` — `sdk.dir` pointer
- `MEMORY/guides/03-android-sdk-install.md` — verified install guide
- `MEMORY/session-logs/2026-06-22_session-05_android-sdk-install.md` — this log

Modified (4):
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` §0 (SDK ✓, JDK ⚠️ blocker)
- `MEMORY/guides/README.md` (guide 03 added)
- `MEMORY/README.md` §7 (quick-links + latest-session pointer)
- `WORKSPACE/DEV/_TEMPLATE/README.md` (local.properties noted)

## Status at end of session

- ✅ Android SDK installed at `/home/z/my-project/ANDROID_SDK` (458 MB), all 3 packages functional.
- ✅ Persistent env file `/home/z/my-project/.android-env.sh` created.
- ✅ `local.properties` template in `_TEMPLATE/DEVELOPMENT_CODE/`.
- ✅ Full install procedure documented in `MEMORY/guides/03-android-sdk-install.md`.
- ⚠️ **`javac` MISSING** — build-time blocker. JRE 21 present, no sudo. User must provide JDK.
- ⏳ First real build still blocked on the JDK.
- ⏳ No extension started yet (no site chosen).

## Next steps (for the next session)

1. **Resolve the JDK blocker** — user to either: (a) grant passwordless sudo so I can
   `apt install openjdk-21-jdk-headless`, (b) install it themselves, or (c) provide a JDK 17/21
   tarball (Adoptium/Temurin) in `upload/` that I extract locally + add to PATH. JDK 17 or 21 both
   satisfy ext-lib v16.
2. **User picks the first target site** → start at `WORKFLOW/01_WEBSITE_RESEARCH/`.
3. **Scaffold the first extension** (`cp -r DEV/_TEMPLATE DEV/<NAME>`) → `WORKFLOW/02_ARCHITECTURE_DESIGN/`.
4. **Build a minimal stub** to close the remaining open verification items (JitPack serves v16,
   tapmoc compat, `core/Source.kt` legacy-override deletion, R8 keep rules).
5. **Reference APK analysis** — when the user directs.

## Open issues

- **`javac` missing (build-time blocker)** — NOT in `TEMPORARY_MEMORY/` because it's not an
  investigation item; it's a known environment gap awaiting user resolution. Documented in
  `MEMORY/guides/03-android-sdk-install.md` §0.1 and flagged in
  `MEMORY/guides/01-build-setup-for-ext-lib-16.md` §0. Once the user provides a JDK, this resolves.
- Other open verification items (JitPack v16, tapmoc, `core/Source.kt` cleanup, R8) — documented in
  `guides/01` §6 and `research/03` §8; close at first real build.

## Honest notes

- **The SDK install followed your exact sequence and succeeded** — all 6 steps, both gotchas handled
  (the rename, the empty-output verification by directories).
- **One unexpected blocker:** `javac` is missing. Your instructions said "Requires JDK 17 with javac
  on PATH" — I should have flagged this BEFORE starting, but I discovered it during the prereq check.
  The good news: it doesn't block the SDK install (sdkmanager runs on JRE), only the Gradle build.
  So the SDK work is fully done; the JDK is a separate, smaller issue for you to resolve.
- **I could not `sudo apt install` the JDK** — no passwordless sudo in this environment. I won't try
  to work around this (rule §5: "Don't mess up the environment"). You'll need to provide it.
- **The SDK is at the exact path you specified** (`/home/z/my-project/ANDROID_SDK`), 458 MB, minimal
  component set (no emulator/NDK/system-images — saves ~3 GB). If a future extension needs any of
  those, `sdkmanager "<pkg>"` adds them.
- **The env file is sourceable, not auto-loaded** — you/I must `source /home/z/my-project/.android-env.sh`
  in each new shell. If you want it auto-loaded, I can add it to `~/.bashrc` (say the word).
