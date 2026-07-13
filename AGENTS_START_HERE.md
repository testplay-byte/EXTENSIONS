# AGENTS_START_HERE.md — Orientation for a new AI agent

> **Read this first.** It orients you to the Aniyomi Extensions project and how to resume work.
> This project is GitHub-hosted: all context, memory, and source live in this repo. No local
> Android SDK is needed — builds happen in GitHub Actions.

---

## 1. What this project is

Three Aniyomi anime-streaming extensions (AniKoto 180, AnimePahe 180, MKissa 180), each built
against **ext-lib v16**, plus a Next.js download webpage deployed to GitHub Pages. APKs are
built and signed by GitHub Actions and published as GitHub Releases.

- **Repo**: https://github.com/testplay-byte/EXTENSIONS
- **Download page**: https://testplay-byte.github.io/EXTENSIONS/
- **Releases**: https://github.com/testplay-byte/EXTENSIONS/releases/latest

## 2. Read these in order (priority)

1. `README.md` (repo root) — project overview
2. `MEMORY/README.md` — knowledge-base navigation map
3. `MEMORY/PROJECT_RULES.md` — **non-negotiable rules** (read before doing anything)
4. `MEMORY/EXTENSIONS.md` — registry of all extensions + their status
5. `EXTENSIONS/HOW_TO_BUILD_EXTENSION/README.md` — the multi-step build guide
6. `MEMORY/guides/04-build-checklist.md` — mandatory pre/post-build checklist
7. `worklog.md` (tail — last ~150 lines) — recent session context

## 3. Current state (3 extensions)

| Extension | Status | Version | Keystore |
|---|---|---|---|
| AniKoto 180 | ✅ Stable | v16.9 (build 9) | `anikoto-release.jks` (alias `anikoto`) |
| AnimePahe 180 | ✅ Stable | v16.10 (build 10) | `animepahe-release.jks` (alias `animepahe`) |
| MKissa 180 | 🚧 In progress (3/6 servers) | v16.17 (build 17) | ⚠️ none yet (debug only) |

## 4. How builds work now (GitHub Actions)

**No local Android SDK.** Builds run in CI.

- **Push to `main`** → `.github/workflows/build.yml` builds debug APKs for all extensions.
- **Tag push** (`v16.x`) → `.github/workflows/release.yml` builds signed release APKs and
  publishes a GitHub Release with the APKs attached.

### Signing secrets (already configured as GitHub Actions secrets)

| Secret | Purpose |
|---|---|
| `ANIKOTO_KEYSTORE_BASE64` | base64-encoded `anikoto-release.jks` |
| `ANIMEPAHE_KEYSTORE_BASE64` | base64-encoded `animepahe-release.jks` |
| `KEYSTORE_PASSWORD` | keystore password (storePassword = keyPassword) |

The `build.gradle.kts` reads `System.getenv("KEYSTORE_PASSWORD")` with a hardcoded fallback for
local builds. Keystores are **never committed** (`.gitignore`'d).

## 5. Critical build rules (from PROJECT_RULES)

1. **extClass** = FULL path (no leading dot) when applicationId ≠ source package.
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE once published.
4. **Video constructor**: ALL 14 named args, `initialized = false`.
5. **Use inherited `client`** (CloudflareInterceptor + cookieJar).
6. **ProGuard**: keep ALL extension classes + `$$serializer` classes.
7. **One change at a time** (project rule §2).
8. **Read HOW_TO_BUILD_EXTENSION** before starting any new extension.
9. **Never commit keystores** (`*.jks`). Never commit `node_modules/`, `build/`, or APKs.

## 6. The download webpage

`src/` is a Next.js app, statically exported and deployed to GitHub Pages by
`.github/workflows/deploy-pages.yml`. Download buttons link to GitHub Release asset URLs:

```
https://github.com/testplay-byte/EXTENSIONS/releases/latest/download/<apk-filename>
```

Design system: dark neon glass-morphism — see `DESIGN_REFERENCE.md`.

## 7. Working on this repo (checklist for a new session)

- [ ] Clone: `git clone https://github.com/testplay-byte/EXTENSIONS.git`
- [ ] Read the files in §2 above
- [ ] For webpage work: `bun install && bun run dev`
- [ ] For extension work: edit Kotlin in `EXTENSIONS/<name>/DEV/src/en/<name>/`
- [ ] To ship a new APK: bump `extVersionCode`, commit, tag `v16.x`, push the tag — CI builds + releases
- [ ] Append your session to `worklog.md` (append a `---`-delimited section)

## 8. Timezone

America/Los_Angeles — interpret relative dates/times in this timezone.
