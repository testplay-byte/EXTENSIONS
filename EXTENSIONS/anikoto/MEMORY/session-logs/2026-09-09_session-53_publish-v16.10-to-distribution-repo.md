# Session 53 — Publish v16.10 to the Confused-Creature-180 distribution repo

**Date:** 2026-09-09 · **Task:** Upload the freshly built AniKoto 180 v16.10 release APK to
`Confused-Creature-180/aniyomi-extensions` so existing v16.9 installs can update in-app.

## Context
Session 52 produced v16.10 (megaplay encrypted getSources fix + 6-domain preferred-domain setting),
tagged `v16.10` on `testplay-byte/EXTENSIONS`. Release workflow run 34351401459 → SUCCESS,
release asset `aniyomi-en.anikoto180-v16.10-release.apk` (270,953 bytes).

## Distribution repo anatomy (observed, NOT modified beyond scope)
- `repo` branch — the real Aniyomi extension repo: `index.json`, `index.min.json`, `repo.json`
  (meta + signingKeyFingerprint), `apk/*.apk`, `icon/`. Users add:
  `raw.githubusercontent.com/Confused-Creature-180/aniyomi-extensions/repo/index.min.json`
- `main` branch — Pages site (`docs/`, loads index.min.json at runtime → no doc edits needed),
  `apk/` direct-download folder, `dev/` (v16.9-era source copy — left untouched per user request)
- No GitHub Releases exist in the distro repo (README "Releases" link is generic) → none created

## Pre-flight verification (before touching anything)
1. **Signature**: `keytool -printcert -jarfile` on new v16.10 vs old v16.9 APK → identical SHA256
   `B4:67:CA:64:...:6A:5A` = repo.json `signingKeyFingerprint` → in-place update OK, no uninstall needed.
2. **Provenance**: tag `v16.10` → commit `e653ea9` → `build.gradle.kts` has `extVersionCode = 10`.
3. **DEX spot-check**: `classes.dex` contains `MegaPlayDecrypt`, `getSourcesNew`,
   `anikototv.se (Nordic mirror)` → the actual fix + domain selector are in the shipped APK.
4. Source id `178825880993122333` unchanged (versionId 11 stable) → no orphaned library entries.

## Changes (minimal diff only)
- `repo` branch commit `3129237`:
  - `apk/aniyomi-en.anikoto180-v16.9-release.apk` → `apk/aniyomi-en.anikoto180-v16.10-release.apk`
    (git saw it as a rename, Bin 268142 → 270953)
  - `index.json` + `index.min.json`: `apk` filename, `code` 9→10, `version` "16.9"→"16.10".
    Everything else (name/pkg/lang/nsfw/sources id/baseUrl) untouched.
- `main` branch commit `e427c5e`: same APK swap in `apk/` (README points users at this folder).

## Post-push verification (live, from outside)
- `raw.../repo/index.min.json` → code 10 / version 16.10 / v16.10 apk ✓
- `raw.../repo/apk/aniyomi-en.anikoto180-v16.10-release.apk` → HTTP 200, 270,953 bytes,
  sha256 `aab6ead4bf41...86ce4` identical to the CI release asset ✓
- `raw.../main/apk/aniyomi-en.anikoto180-v16.10-release.apk` → HTTP 200 ✓
- Pages site renders from the same index → shows v16.10 automatically.

## Stage Summary
- ★ Aniyomi users on v16.9 will get an update prompt (code 10 > 9); update installs over the old
  APK without uninstall (identical signing cert).
- ★ dev/ source in the distro repo intentionally left at v16.9 (user asked for APK only —
  "don't mess with this repo too much").
- ★ ntfy.sh notification sent to topic `THE-TASK-IS-DONE`.

## Addendum (same session) — Pages site update + hardening (commits 1c8339c, 4d32f86)
- User reported the site still looked stale (hit it during the ~30-min Pages rebuild + browser cache).
- Verified live in a real browser BEFORE changes: cards already rendered v16.10 from the index (site is fully dynamic, no hardcoded versions anywhere).
- Improvements shipped:
  - `docs/js/app.js`: cache-busted index fetch (`?t=` + `cache:'no-cache'`) — site can never show a stale release again; `syncWhatsNew()` keeps the release banner version + download link in lockstep with the repo index.
  - `docs/index.html` + `docs/extensions.html`: "Latest Update" banner — v16.10 changelog (playback fix, 6-domain setting), direct APK download button, in-app update hint.
  - `docs/css/style.css`: `.whatsnew` styles (Cream Notebook tokens, washi-tape accent, responsive, dark-mode via tokens).
  - Mobile overflow FIXES (pre-existing bugs found during verification): repo-url-box code wrapped (was 722px wide on 390px screens), install-methods grid `minmax(min(320px,100%),1fr)`, method-card min-width:0, `overflow-x: clip` safety net.
- Format cross-check per user request: official aniyomiorg `repo` branch index.min.json has the IDENTICAL field set (apk, code, lang, name, nsfw, pkg, sources / baseUrl, id, lang, name) → our index is spec-compliant for in-app updates. (yuzono has no `repo` branch index — non-blocking.)
- Verified after Pages rebuild (agent-browser, fresh contexts): desktop + 390px mobile, light + dark, scrollW==clientW (overflow gone), banner badge v16.10, download href = v16.10 APK (byte-verified earlier), card badges v16.10.
- NOTE for future: full-page stitched screenshots don't trigger IntersectionObserver reveals — sections LOOK blank in --full captures; scroll + viewport screenshots are the reliable check.

## Addendum 2 (same session) — user-reported regressions: download 404s + stale 16.9 + wrong icon
Commits: repo branch `f16cf44`, main `378bb6e` + distro Release `v16.10` (id 385571039).

### Root causes found (verified against app source, not guessed)
1. **Download 404s ("can't download" / "version shows 16.9")**: publishing v16.10 DELETED the v16.9 APK
   from `repo/apk/`. AnimeExtensionApi (aniyomiorg/aniyomi, Animiru is a fork) gates repo checks to
   ONCE PER DAY (`last_ext_check` + 24h) — so every client that fetched the index before the publish
   still held `apk: …v16.9-release.apk` and got HTTP 404 on Install/Update. Browsers with cached
   pages hit the same 404. → FIX: keep old APKs alongside new ones (v16.9 restored next to v16.10 on
   both `repo/apk/` and `main/apk/`). RULE for future publishes: NEVER delete the previous APK.
2. **Wrong icon pre-install**: the app builds icon URLs as `{indexBase}/icon/{pkg}.png` from the repo
   branch; that file was the "custom 1-8-0 branded" artwork (md5 d6706…) while the actual APK launcher
   icon is the flower (md5 b14f03… — identical to dev-source mipmap-xxxhdpi and the site's bundled
   icon). → FIX: repo-branch icon replaced with the real APK icon. Same file now everywhere.
3. **NOT bugs** (verified from source + ground truth): repo.json shape is correct — official
   aniyomiorg repo.json has the identical {meta:{name,website,signingKeyFingerprint}} shape;
   index.min.json field set matches NetworkLegacyAnimeExtension exactly; URL schemes
   `{base}/apk/{apk}` + `{base}/icon/{pkg}.png` match our layout.

### Extra deliverable
- GitHub Release `v16.10` created on the distro repo with the signed APK (README already advertised a
  Releases page; release assets download reliably on mobile via Content-Disposition).

### Verified live after push
v16.9 APK 200 (268,142b) · v16.10 APK 200 (270,953b) · icon 200 md5 b14f03… · index code=10 v16.10 ·
repo.json 200 · release asset 200 · main apk/ both 200.

### User guidance (app refresh)
App checks repos ≤1×/24h. To force now: Settings → Browse → Extension repos → remove + re-add the
repo (guaranteed fresh index) — or wait ≤24h for the automatic check. Update installs over v16.9
(same signing key).
