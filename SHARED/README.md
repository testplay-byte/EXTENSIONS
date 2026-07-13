# SHARED/ — Cross-extension reference resources

> **These folders are NOT committed to git** (see root `.gitignore`).
> They are large, regeneratable, third-party reference material. Re-clone locally when needed.

## SHARED/REFERENCE_HUB/ — Cloned reference repositories (read-only)

Reference source code consulted during extension development. Clone fresh with:

```bash
cd SHARED/REFERENCE_HUB

# Aniyomi app (the player that loads our extensions)
git clone --depth 1 https://github.com/aniyomiorg/aniyomi.git aniyomi-app

# Aniyomi extensions library (the ext-lib v16 API we build against)
git clone --depth 1 https://github.com/aniyomiorg/aniyomi-extensions.git aniyomi-extensions-lib

# Keiyoushi extensions (modern community fork — utility helpers reference)
git clone --depth 1 https://github.com/keiyoushi/extensions.git ext-lib-keiyoushi

# Aniyomiorg anime extensions (community anime extension sources)
git clone --depth 1 https://github.com/aniyomiorg/extensions.git ext-lib-aniyomiorg

# Komikku extensions (another fork — alternate patterns reference)
git clone --depth 1 https://github.com/komikku-app/extensions.git ext-lib-komikku-new

# Older anime-extensions reference (historical patterns)
git clone --depth 1 https://github.com/aniyomiorg/anime-extensions.git anime-extensions-ref
```

> If a repo has moved or been renamed, search GitHub for the org (`aniyomiorg`, `keiyoushi`,
> `komikku-app`) — the reference repos are well-known Aniyomi ecosystem projects.

**Rules (from `MEMORY/PROJECT_RULES.md`):**
- These are **read-only references**. Never edit files here.
- **Never copy code** from reference APKs/repos. Build from understanding; cross-check only.
- Cite the reference file path in comments when a technique is derived from one.

## SHARED/APK_REFERENCE/ — Reference APKs (cross-check only)

Two previously-published AniKoto APKs kept for reverse-engineering comparison.
**NEVER commit these.** Re-obtain from the original publishers if needed; analysis
notes live in `MEMORY/research/`.

| File | What |
|------|------|
| `anikoto-by-1118000-v3.apk` | Debug build by `1118000`, v16.1 — non-obfuscated, clean jadx output |
| `anikoto-refrence-v16.4.apk` | Release build by `salmanbappi`, v16.4 — R8-obfuscated |
