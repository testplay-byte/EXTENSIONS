# RESTORE Guide — Aniyomi Extensions Project (AniKoto + AnimePahe + MKissa)

> **Read this first when starting a new session.** This zip contains everything needed to resume work
> on the Aniyomi Extensions project — three anime streaming extensions (AniKoto 180 + AnimePahe 180 +
> MKissa 180) plus the complete build guide for creating more.

## ⚡ Quick Start

1. Extract the 7z into `/home/z/my-project/` (strip the top-level folder prefix)
2. Read `STARTUP_PROMPT.md` — paste it as your first message to the AI
3. Reinstall JDK 17 + Android SDK (commands below — they don't survive backup)
4. Verify the builds:
   ```bash
   source /home/z/my-project/.android-env.sh
   cd EXTENSIONS/anikoto/DEV && ./gradlew :src:en:anikoto:assembleRelease --no-daemon
   cd ../../animepahe/DEV && ./gradlew :src:en:animepahe:assembleRelease --no-daemon
   cd ../../mkissa/DEV && ./gradlew :src:en:mkissa:assembleDebug --no-daemon
   ```
5. Start the Next.js download webpage: `bun run dev` (port 3000)
6. Read the latest session logs + worklog.md for context

## What's in this zip

| Path | What | Size |
|------|------|------|
| `EXTENSIONS/` | ★ Per-extension workspaces — anikoto/ (✅ stable) + animepahe/ (✅ stable) + mkissa/ (🚧 in progress) + _template/ + HOW_TO_BUILD_EXTENSION/ | ~51M |
| `MEMORY/` | ★ Project-level knowledge base — README, PROJECT_RULES, EXTENSIONS registry, guides, decisions, ext-lib, research, build-env | ~340K |
| `SHARED/` | Cross-extension reference resources — REFERENCE_HUB/ (cloned repos, read-only) + APK_REFERENCE/ (reference APKs) | ~67M |
| `src/` + `public/` + `package.json` etc. | ★ Next.js download webpage — shows all 3 extensions with download buttons | ~5M |
| `STARTUP_PROMPT.md` | ★ The prompt to paste to the AI to start the session | — |
| `RESTORE.md` | This file | — |
| `PROJECT_INDEX.md` | Quick top-level pointer | — |
| `worklog.md` | ★ Shared worklog — all accumulated context from every session | ~3.3M |
| `.android-env.sh` | Environment setup script (JAVA_HOME + ANDROID_HOME paths) | 1K |

## What's NOT in this zip (recreate after restore)

| Path | Why excluded | How to recreate |
|------|-------------|-----------------|
| `JDK/jdk-17.0.13+11/` | 316M binary — doesn't survive backup | Download Temurin JDK 17.0.13+11 (see below) |
| `ANDROID_SDK/` | 604M binary — doesn't survive backup | Install via sdkmanager (see below) |
| `node_modules/` | npm packages — recreatable | `bun install` (Next.js project) |
| `.next/` | Next.js build cache — recreatable | Auto-created on `bun run dev` |
| `EXTENSIONS/*/DEV/build/` | Gradle build outputs — recreatable | `./gradlew assembleDebug` or `assembleRelease` |
| `EXTENSIONS/*/DEV/.gradle/` | Gradle cache — recreatable | Auto-created on first build |
| `*.jks` | Keystores (in .gitignore — but ARE included in this zip for continuity) | — |

## How to restore (6 steps)

### Step 1 — Extract the 7z into `/home/z/my-project/`

```bash
cd /home/z/my-project
# The backup is a .7z — use py7zr if 7z CLI is unavailable:
#   pip install py7zr && python -m py7zr x <backup-file>.7z
# Then move the top-level folder's contents up into /home/z/my-project/ (strip the prefix).
```

### Step 2 — Reinstall JDK 17 + Android SDK (they don't survive backup)

**JDK 17 (Temurin 17.0.13+11):**
```bash
mkdir -p /home/z/my-project/JDK
cd /tmp
curl -fL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/version/jdk-17.0.13+11/linux/x64/jdk/hotspot/normal/eclipse"
cd /home/z/my-project/JDK && tar -xzf /tmp/jdk17.tar.gz
# Verify:
JDK/jdk-17.0.13+11/bin/javac -version  # → javac 17.0.13
```

**Android SDK** (follow `MEMORY/guides/03-android-sdk-install.md` exactly):
```bash
export ANDROID_HOME=/home/z/my-project/ANDROID_SDK
mkdir -p $ANDROID_HOME/cmdline-tools
cd /tmp
wget -c "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q /tmp/commandlinetools-linux-11076708_latest.zip -d $ANDROID_HOME/cmdline-tools
mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest  # CRITICAL rename
source /home/z/my-project/.android-env.sh
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### Step 3 — Build the extensions to verify

```bash
source /home/z/my-project/.android-env.sh

# AniKoto 180
cd /home/z/my-project/EXTENSIONS/anikoto/DEV
./gradlew :src:en:anikoto:assembleRelease --no-daemon

# AnimePahe 180
cd /home/z/my-project/EXTENSIONS/animepahe/DEV
./gradlew :src:en:animepahe:assembleRelease --no-daemon

# MKissa 180 (debug only — no keystore yet)
cd /home/z/my-project/EXTENSIONS/mkissa/DEV
./gradlew :src:en:mkissa:assembleDebug --no-daemon
```

### Step 4 — Verify the build checklist

```bash
cat MEMORY/guides/04-build-checklist.md
```

### Step 5 — Read the latest session logs + worklog

```bash
# MKissa latest: session 17
ls -t EXTENSIONS/mkissa/MEMORY/session-logs/ | head -3

# Full worklog (all sessions, all extensions)
tail -100 worklog.md
```

### Step 6 — Start the Next.js download webpage

```bash
cd /home/z/my-project
bun install    # install npm packages (if node_modules/ not present)
bun run dev   # starts on port 3000 — serves the APK download page at /
```

The webpage shows all 3 extensions (AniKoto 180 + AnimePahe 180 + MKissa 180) with their icons, status badges,
and download buttons (release + debug APKs).

## Current project status

### AniKoto 180 (v16.9, Build 7, versionCode 9, versionId 11) — ✅ STABLE
- Package: `eu.kanade.tachiyomi.animeextension.en.anikoto180`
- Site: anikototv.to
- Features: 4 video servers + Kiwi-Stream, smart search, episode metadata, fork compatibility, R8 release, signed

### AnimePahe 180 (v16.10, Build 10, versionCode 10, versionId 1) — ✅ STABLE
- Package: `eu.kanade.tachiyomi.animeextension.en.animepahe180`
- Site: animepahe.pw
- Features: popular, latest, search, filters, details, episodes, Kwik HLS video playback, metadata enrichment

### MKissa 180 (v16.17, Build 17, versionCode 17, versionId 1) — 🚧 IN PROGRESS
- Package: `eu.kanade.tachiyomi.animeextension.en.mkissa180`
- Site: mkissa.to (SvelteKit frontend on api.allanime.day GraphQL API)
- Features: catalog + details + episodes + metadata enrichment + 3/6 video servers working
- Working servers: Ok.ru ✅, Mp4Upload ✅, Vn-Hls ✅
- In-progress: Fm-Hls (loadAndExtractVideo), Uni (loadAndExtractVideo + ad blocking), Luf-Mp4 (CF-dependent)
- Key challenge: Cloudflare Turnstile on watch page — uses native MotionEvent auto-click

## Key files to read first (priority order)

1. **`STARTUP_PROMPT.md`** — ★ the prompt to paste to the AI
2. **`MEMORY/README.md`** — the starting guide + navigation map
3. **`MEMORY/PROJECT_RULES.md`** — non-negotiable rules
4. **`MEMORY/EXTENSIONS.md`** — registry of all extensions + status
5. **`EXTENSIONS/HOW_TO_BUILD_EXTENSION/README.md`** — ★ the multi-step build guide
6. **`MEMORY/guides/04-build-checklist.md`** — ★ MANDATORY pre/post-build checklist
7. Latest session logs (all extensions)
8. `worklog.md` tail — recent context

## Environment notes

- **Timezone**: America/Los_Angeles
- **Dev server**: Next.js runs on port 3000 in the background (`bun run dev`) — serves the APK download page
- **Build commands**: `./gradlew :src:en:<name>:assembleDebug` or `assembleRelease`
- **gradle-wrapper.jar**: does NOT survive backup — regenerate with `gradle wrapper --gradle-version 8.14.3` (Gradle dist cached at /tmp/gradle-8.14.3 or re-download)
- **Keystores**: included in the zip (anikoto-release.jks + animepahe-release.jks) — keep secure

## ⚠️ Keystore safety

Both keystores are included in this backup. They are the signing keys for the extensions.
**Keep this backup secure.** If someone obtains a keystore + password
(`Confused1118000Creature.xyz`), they could sign malicious APKs as your extensions.

The keystores are in `.gitignore` — they will NOT be committed to git. But they ARE in this zip.
Store this zip in a secure location.
