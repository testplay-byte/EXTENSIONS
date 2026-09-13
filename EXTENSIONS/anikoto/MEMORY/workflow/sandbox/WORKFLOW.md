# WORKFLOW.md — AniKoto 180 maintenance: start → finish (including publishing)

> The exact process that produced v16.10 (session 52–53), v16.11 (session 54) and the v16.12
> Smart Search release (sessions 56–58), written down so any future session reproduces it
> identically. Follow phases in order; don't skip verification steps. Companion docs:
> `REFERENCE.md` (facts/paths), `TROUBLESHOOTING.md` (per-issue playbooks), `TOOLS.md`
> (scrapling/scrapegraphai), `SECRETS.md` (tokens).

---

## ⭐ GOLDEN RULES (read before touching anything)

1. **Never change** source ID `178825880993122333` / `extVersionId = 11` / package name — it
   orphan every user's library.
2. **Never delete a previously published APK** from the dist repo — clients cache the index up
   to 24 h and will 404 on the old filename (this broke downloads once already, session 53c).
3. **Never break the signing key** — cert SHA256 `B4:67:CA:…:6A:5A` must match repo.json so
   updates install in place.
4. **Distribution repo = surgical commits only** ("handle with care"). Dev repo = free.
5. **Verify live before AND after** every publish (curl matrix, sha256, signature).
6. **Bump `extVersionCode` on every release**; display version = `16.x` matching tag `v16.x`.
   (★ Exception, session 58: the user may explicitly REUSE a test-build number for a release —
   test numbers are NOT reserved; 16.12 was published after test builds 16.12/16.13.)
7. **Ground truth beats theory**: diagnose with real requests (curl/python replay of the app's
   exact chain), not assumptions. LLM tools assist, never decide (TOOLS.md §4).
8. **Fresh browser contexts + cache-bust** when checking the Pages site; raw.githubusercontent
   and Pages both cache (300 s / ~30 min build window).
9. Write a **session log + worklog entry + ntfy ping** at the end of every session. No exceptions.
10. When the sandbox resets, re-clone with the PATs in SECRETS.md and read the latest
    `MEMORY/session-logs/` BEFORE doing anything.

---

## Phase 0 — Sandbox restore / environment check

Do this at session start (always, even if things "should" be there):

```bash
# 0.1 Are the repos there?
git -C /home/z/extensions-repo status -sb 2>/dev/null || echo MISSING-DEV
git -C /home/z/aniyomi-extensions-180 status -sb 2>/dev/null || echo MISSING-DIST
```

If missing, clone (PATs in `SECRETS.md`):
```bash
git clone https://<PAT1>@github.com/testplay-byte/EXTENSIONS /home/z/extensions-repo
git -C /home/z/extensions-repo remote set-url origin https://github.com/testplay-byte/EXTENSIONS
# push later with: git -C ... push https://<PAT1>@github.com/testplay-byte/EXTENSIONS main

git clone https://<PAT2>@github.com/Confused-Creature-180/aniyomi-extensions /home/z/aniyomi-extensions-180
git -C /home/z/aniyomi-extensions-180 remote set-url origin https://github.com/Confused-Creature-180/aniyomi-extensions
# note: dist repo has TWO branches to fetch: git -C ... fetch origin repo main
```

```bash
# 0.2 Sync + read history
git -C /home/z/extensions-repo pull --ff-only
git -C /home/z/aniyomi-extensions-180 fetch origin && git -C /home/z/aniyomi-extensions-180 status -sb
# Read the newest session logs:
#   EXTENSIONS/anikoto/MEMORY/session-logs/  (sort by date; read the last 1–2)
# 0.3 Tools sanity
python3 --version && uv --version
source /home/z/scrape-venv/bin/activate && python -c "import scrapling, scrapegraphai" && echo TOOLS-OK
```

## Phase 1 — Intake & triage

1. Capture the user report verbatim (what broke: browse? search? episodes? playback?
   download? icon? website?). Ask for `adb logcat -s Anikoto` output if playback/browsing.
2. Classify:
   - **A. Extension runtime** (browse/search/episodes/playback) → Phase 2A
   - **B. Distribution/update path** (in-app download, stale version, 404s) → Phase 2B
   - **C. Website/Pages** → Phase 2C
   - **D. Cosmetic** (icon, texts, settings UI) → Phase 2D
3. Multiple reports → triage all, fix in one release (bump version once).

## Phase 2 — Diagnosis (evidence first, no code changes yet)

### 2A. Extension runtime (the "extension broke" playbook)
Work through the chain in `REFERENCE.md` §5 with curl/python, one stage per command,
recording HTTP status + shape. Full step-by-step in `TROUBLESHOOTING.md` §1.
Key stages: domain alive → watch page `data-id` → episode list ajax (RC4 vrf) →
server list → server iframe → megaplay page → getSources(enc) → decrypt → master m3u8
**fetchable?** (the v16.11 lesson: default CDN 403s non-browser TLS; the `s=` param decides) →
variant m3u8 → one segment (PNG-wrap check).

- If **HTML structure** changed → scrapling adaptive probing (`TOOLS.md` §2) to regenerate
  selectors.
- If **API/JSON shape** changed → capture raw response, optionally scrapegraphai mapping
  (`TOOLS.md` §3), then hand-verify.
- Cross-check the running code: does current `AnikotoExtractors.kt` handle what you observed?
  (Read the file — don't assume last session's fix covers the new case.)

### 2B. In-app download / stale version
1. `curl -s <RAW_REPO>/index.min.json | python3 -m json.tool` → does `code`/`version`/`apk`
   match the newest release? If not → publish didn't land (Phase 4 redo).
2. If index is right but user sees old version → it's the **24 h app cache**; instruct
   remove + re-add repo (Phase 5 user guidance).
3. `curl -sI <RAW_REPO>/apk/<apk-field>` for EVERY apk filename in the index → any 404 means
   an old APK was deleted (Golden Rule 2 violated) → restore from staging/GitHub Release.

### 2C. Website
1. Fresh context, cache-busted: site + `assets/js` + index fetch.
2. Check banner version == index version == newest APK present in `main/apk/`.
3. Mobile 390 px: no horizontal overflow (scrollWidth == clientWidth).
4. Remember: Pages build window ~30 min after push; raw cache ~300 s.

### 2D. Cosmetic (icon etc.)
- Pre-install icon comes from `{indexBase}/icon/{pkg}.png` — must equal the APK launcher
  icon (md5 `b14f03…` today). Compare before "fixing" (session 53c has the full method).

### 2A′. Fast reproduce (optional but valuable)
Simulate the app chain in python (pattern: sessions 52–54 session logs) for 2 titles before
claiming a fix works.

## Phase 3 — Fix & build (dev repo)

```bash
cd /home/z/extensions-repo/EXTENSIONS/anikoto/DEV
# 3.1 edit Kotlin (usually AnikotoExtractors.kt / Anikoto.kt / AnikotoSettings.kt / DTOs)
# 3.2 bump version in src/en/anikoto/build.gradle.kts:  extVersionCode = N+1,  extVersionName = "16.N+1"
# 3.3 local sanity build (optional, CI is the real gate):
./gradlew assembleRelease
# 3.4 commit + tag + push
git add -A && git commit -m "anikoto v16.N+1: <one-line root cause fix>"
git tag v16.N+1 && git push origin main v16.N+1
```

CI builds the release: watch
`https://github.com/testplay-byte/EXTENSIONS/actions` (gh CLI or web). On success the
Release `v16.N+1` gets asset `aniyomi-en.anikoto180-v16.N+1-release.apk`.

```bash
# 3.5 stage + verify the artifact
mkdir -p /home/z/apk-staging && cp <asset> /home/z/apk-staging/
sha256sum /home/z/apk-staging/*.apk
keytool -printcert -jarfile /home/z/apk-staging/*.apk | grep SHA256     # must be B4:67:CA:…
unzip -p <apk> classes.dex | strings | grep -E '<NEW-SYMBOL|tcdn|MegaPlayDecrypt' | head   # fix actually shipped
```

## Phase 4 — Publish to the distribution repo (SURGICAL)

```bash
cd /home/z/aniyomi-extensions-180
git checkout repo && git pull origin repo
# 4.1 APK: ADD the new one, keep ALL old ones
cp /home/z/apk-staging/aniyomi-en.anikoto180-v16.N+1-release.apk apk/
# 4.2 index.json + index.min.json: update ONLY apk filename, code, version (nothing else)
# 4.3 icon: only touch if the APK icon actually changed (REFERENCE.md §3)
git add apk index.json index.min.json
git commit -m "AniKoto 180 v16.N+1: <one-line>" && git push origin repo

git checkout main && git pull origin main
cp /home/z/apk-staging/aniyomi-en.anikoto180-v16.N+1-release.apk apk/     # keep old ones
# 4.4 Pages banner: docs/js/app.js syncWhatsNew() auto-syncs from the index — normally NO doc edit needed;
#     only edit docs/index.html text if the changelog bullet should change.
git add apk docs && git commit -m "Pages + apk/: AniKoto 180 v16.N+1" && git push origin main

# 4.5 distro GitHub Release (mobile-friendly Content-Disposition download)
gh release create v16.N+1 apk/aniyomi-en.anikoto180-v16.N+1-release.apk \
  --title "AniKoto 180 v16.N+1" --notes "<what changed>" \
  -R Confused-Creature-180/aniyomi-extensions
```

## Phase 5 — Verify end-to-end (never skip)

```bash
RAW=https://raw.githubusercontent.com/Confused-Creature-180/aniyomi-extensions/repo
# 5.1 index correctness
curl -s $RAW/index.min.json | python3 -m json.tool | head -20        # code/version/apk correct?
# 5.2 EVERY apk in index → 200 + sha matches staging
curl -s $RAW/apk/aniyomi-en.anikoto180-v16.N+1-release.apk -o /tmp/a.apk && sha256sum /tmp/a.apk
# 5.3 icon + repo.json + main-branch apk mirror + distro release asset → all 200
# 5.4 site: fresh-context browser check (desktop + 390px mobile, light + dark):
#     banner shows v16.N+1, download href = new APK, cards render, no overflow
```

User guidance to include in the reply: app checks repos ≤1×/24 h → **remove + re-add the repo**
in Settings → Browse → Extension repos for an instant refresh; update installs in place
(same signing key).

## Phase 6 — Memory & notify

1. Write `EXTENSIONS/anikoto/MEMORY/session-logs/YYYY-MM-DD_session-NN_<slug>.md`
   (restore, diagnosis table, root cause, fix, release IDs, stage summary with ★ lessons).
2. Append to `EXTENSIONS/anikoto/MEMORY/worklog.md` **and** repo-root `worklog.md`
   (`---` + Task ID + Work Log + Stage Summary template).
3. Commit + push dev repo docs.
4. `curl -s -d "<one-line result>" https://ntfy.sh/THE-TASK-IS-DONE`

## Phase 7 — Where the advanced tools plug in

See `TOOLS.md` §4. Short version: scrapling = selector healing during Phase 2A;
scrapegraphai = LLM mapping of changed API/HTML schemas during Phase 2A; both feed
**verified** evidence into Phase 3 — raw-request verification stays mandatory.
