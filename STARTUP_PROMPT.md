# STARTUP PROMPT — Paste this as your first message to the AI

I'm restoring a session for the **Aniyomi Extensions** project (AniKoto 180 + AnimePahe 180 + MKissa 180).
Here's what you need to do:

## 1. Restore the environment

1. Extract the backup into `/home/z/my-project/` (if not already done)
2. Read `MEMORY/README.md` (the starting guide)
3. Read `MEMORY/PROJECT_RULES.md` (non-negotiable rules)
4. Read `MEMORY/EXTENSIONS.md` (the extensions registry — shows all 3 extensions + their status)
5. Read `MEMORY/guides/04-build-checklist.md` (★ MANDATORY pre/post-build checklist)
6. Reinstall JDK 17 + Android SDK if they're missing (see `RESTORE.md` §Step 2 — exact commands there)
7. Verify the builds work:
   ```bash
   source /home/z/my-project/.android-env.sh

   # AniKoto 180
   cd EXTENSIONS/anikoto/DEV
   ./gradlew :src:en:anikoto:assembleRelease
   # → produces aniyomi-en.anikoto180-v16.9-release.apk (signed)

   # AnimePahe 180
   cd ../../animepahe/DEV
   ./gradlew :src:en:animepahe:assembleRelease
   # → produces aniyomi-en.animepahe180-v16.10-release.apk (signed)

   # MKissa 180
   cd ../../mkissa/DEV
   ./gradlew :src:en:mkissa:assembleDebug
   # → produces aniyomi-en.mkissa180-v16.17-debug.apk
   ```

## 2. Current state — 3 extensions

### AniKoto 180 (v16.9, Build 7, versionCode 9, versionId 11) — ✅ STABLE
- Package: `eu.kanade.tachiyomi.animeextension.en.anikoto180`
- Site: anikototv.to
- Features: 4 video servers, smart search, episode metadata, R8 release, signed
- Latest session log: `EXTENSIONS/anikoto/MEMORY/session-logs/2027-06-27_session-51_*.md`

### AnimePahe 180 (v16.10, Build 10, versionCode 10, versionId 1) — ✅ STABLE
- Package: `eu.kanade.tachiyomi.animeextension.en.animepahe180`
- Site: animepahe.pw
- Features: popular, search, filters, Kwik HLS video playback, metadata enrichment
- Latest session log: `EXTENSIONS/animepahe/MEMORY/session-logs/2027-06-28_session-09_finalized.md`

### MKissa 180 (v16.17, Build 17, versionCode 17, versionId 1) — 🚧 IN PROGRESS
- Package: `eu.kanade.tachiyomi.animeextension.en.mkissa180`
- Site: mkissa.to (SvelteKit frontend on api.allanime.day GraphQL API)
- Features: catalog + details + episodes + metadata enrichment + 3/6 video servers working
- Working servers: Ok.ru ✅, Mp4Upload ✅, Vn-Hls (vidnest.io) ✅
- In-progress servers: Fm-Hls (loadAndExtractVideo), Uni (loadAndExtractVideo + ad blocking), Luf-Mp4 (CF-dependent)
- Latest session log: `EXTENSIONS/mkissa/MEMORY/session-logs/` (17 sessions)
- Key challenge: Cloudflare Turnstile on the watch page — uses native MotionEvent to auto-click

## 3. Project structure (3 extensions)

```
/home/z/my-project/
├── EXTENSIONS/
│   ├── HOW_TO_BUILD_EXTENSION/   ← build guide (updated with MKissa lessons)
│   ├── _template/
│   ├── anikoto/                  ← ✅ stable
│   ├── animepahe/                ← ✅ stable
│   └── mkissa/                   ← 🚧 in progress (video playback)
├── MEMORY/                       ← project-level knowledge base
├── SHARED/                       ← reference repos + APKs (read-only)
├── src/ public/                  ← Next.js download webpage (3 extensions)
├── JDK/ ANDROID_SDK/ .android-env.sh
└── worklog.md PROJECT_INDEX.md STARTUP_PROMPT.md RESTORE.md
```

## 4. Critical build rules

1. **extClass** = FULL path (no leading dot) when applicationId ≠ source package.
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE once published.
4. **Video constructor**: ALL 14 named args, `initialized = false`.
5. **Use inherited `client`** (CloudflareInterceptor + cookieJar).
6. **ProGuard**: keep ALL extension classes + `$$serializer` classes.
7. **One change at a time** (project rule §2).
8. **Read the HOW_TO_BUILD_EXTENSION guide** before starting any new extension.

## 5. What to do next

The user will tell you the next task. For MKissa, the main remaining work is:
- Getting Fm-Hls, Uni, and Luf-Mp4 servers fully working on-device
- Testing the Cloudflare Turnstile auto-click (native MotionEvent)
- Generating the release keystore + building a signed release APK

Await instructions.
