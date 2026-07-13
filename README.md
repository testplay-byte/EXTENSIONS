# Aniyomi Extensions

> Aniyomi / Animiru anime streaming extension APKs — built, signed, and distributed via GitHub Actions + GitHub Releases. Download page hosted on GitHub Pages.

[**🌐 Download Page**](https://testplay-byte.github.io/EXTENSIONS/) · [**📦 Latest Releases**](https://github.com/testplay-byte/EXTENSIONS/releases/latest)

---

## Extensions

| Extension | Site | Status | Version |
|---|---|---|---|
| **AniKoto 180** | anikototv.to | ✅ Stable | v16.9 (build 9) |
| **AnimePahe 180** | animepahe.pw | ✅ Stable | v16.10 (build 10) |
| **MKissa 180** | mkissa.to | 🚧 In progress | v16.17 (build 17) |

Each extension is an Aniyomi anime source built against **ext-lib v16**. Install the APK from
Aniyomi's extension manager, or download it directly from the [releases page](https://github.com/testplay-byte/EXTENSIONS/releases/latest).

---

## Repository structure

```
EXTENSIONS/
├── anikoto/        ← AniKoto 180 (Gradle project + per-ext knowledge base)
├── animepahe/      ← AnimePahe 180
├── mkissa/         ← MKissa 180 (in progress)
├── _template/      ← scaffold for new extensions
└── HOW_TO_BUILD_EXTENSION/   ← step-by-step build guide
MEMORY/             ← project-level knowledge base (rules, guides, decisions, research)
SHARED/             ← reference repos + APKs (NOT committed — see SHARED/README.md)
src/ public/        ← Next.js download webpage (static export → GitHub Pages)
.github/workflows/  ← CI: build APKs + publish releases + deploy Pages
prisma/ db/         ← (webpage uses Prisma/SQLite if needed)
```

## Building APKs

APKs are built by **GitHub Actions** — no local Android SDK required.

- **On push to `main`**: CI builds debug APKs for all extensions (sanity check).
- **On tag push** (e.g. `v16.9`): CI builds signed release APKs and publishes a GitHub Release
  with the APKs attached as downloadable assets.

To trigger a release locally:
```bash
git tag v16.9
git push origin v16.9
```

### Required GitHub secrets (for release signing)

| Secret | Value / how to set |
|---|---|
| `ANIKOTO_KEYSTORE_BASE64` | `base64 < anikoto-release.jks` |
| `ANIMEPAHE_KEYSTORE_BASE64` | `base64 < animepahe-release.jks` |
| `KEYSTORE_PASSWORD` | the keystore password (storePassword = keyPassword) |

Keystores are **never committed** (`.gitignore`'d). They live only as GitHub Actions secrets.

> MKissa has no release keystore yet — it builds debug-only until a `mkissa-release.jks` is
> generated and added as the `MKISSA_KEYSTORE_BASE64` secret.

## The download webpage

A Next.js app (`src/`) exported as a static site and deployed to **GitHub Pages**.
The download buttons link directly to GitHub Release assets, so they always serve the latest
signed APKs from GitHub's CDN.

- **Local dev**: `bun install && bun run dev` (port 3000)
- **Design**: dark neon glass-morphism — see `DESIGN_REFERENCE.md`

## For a new AI agent starting on this repo

Read these in order (see `AGENTS_START_HERE.md` for the full guide):

1. `AGENTS_START_HERE.md` — orientation + how to resume work
2. `MEMORY/README.md` — knowledge-base navigation
3. `MEMORY/PROJECT_RULES.md` — non-negotiable rules
4. `MEMORY/EXTENSIONS.md` — extension registry + status
5. `EXTENSIONS/HOW_TO_BUILD_EXTENSION/README.md` — the build guide
6. `worklog.md` (tail) — recent session context

## License

Proprietary — source available for the project owner. All rights reserved.
