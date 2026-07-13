# build-env/ — Build Environment State

> **Current state of the build toolchain.** Where the JDK and Android SDK are installed and how to
> activate them. For the install *procedure*, see [`../guides/03-android-sdk-install.md`](../guides/03-android-sdk-install.md).

---

## Current install (this machine)

| Component | Path | Version | Notes |
|---|---|---|---|
| **JDK** | `/home/z/my-project/JDK/jdk-17.0.13+11/` | Temurin 17.0.13+11 | Required for ext-lib 16 builds |
| **Android SDK** | `/home/z/my-project/ANDROID_SDK/` | cmdline-tools + platform-tools + android-34 + build-tools;34.0.0 | |
| **Env script** | `/home/z/my-project/.android-env.sh` | — | Source this before every build |

## Activate the environment

```bash
source /home/z/my-project/.android-env.sh
```

This sets `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and updates `PATH`.

Verify:
```bash
javac -version        # → javac 17.0.13
sdkmanager --version  # → installed
```

## What the env script does

```sh
export ANDROID_HOME=/home/z/my-project/ANDROID_SDK
export ANDROID_SDK_ROOT=/home/z/my-project/ANDROID_SDK
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
export JAVA_HOME=/home/z/my-project/JDK/jdk-17.0.13+11
export PATH="$JAVA_HOME/bin:$PATH"
```

## Reinstalling (if missing — they don't survive backups)

The JDK and Android SDK are large binaries excluded from project backups. To reinstall, follow
[`../guides/03-android-sdk-install.md`](../guides/03-android-sdk-install.md) exactly, or see the
top-level `RESTORE.md` §Step 2 for the quick commands.

## Notes

- The env script lives at the **project root** (not here) because it's sourced in every build shell —
  ergonomics matter. This folder documents its *state*.
- The `JDK/` and `ANDROID_SDK/` folders are at the project root (not inside `EXTENSIONS/`) because
  they're shared across ALL extensions — every extension builds with the same toolchain.
