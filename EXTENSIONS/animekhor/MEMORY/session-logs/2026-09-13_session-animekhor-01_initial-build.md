# Session animekhor-01 — AnimeKhor 180 initial build (branch ext/animekhor)

> Date: 2026-09-13 · Mode: BUILD (branch only — NO merge to main, NO publish, per user:
> "create a new branch and in that branch create a new extension for https://animekhor.org/
> …dont merge the branch with the main branch")

---

## 1. What was asked

Create a new Aniyomi extension for https://animekhor.org/ on a dedicated branch, handled with
the full project workflow. Leave the branch unmerged.

## 2. Site analysis (the hard part — Cloudflare)

- animekhor.org is behind a **Cloudflare managed challenge (Turnstile)** on every path.
- Probes that FAILED from the sandbox: plain curl (403), scrapling StealthyFetcher headless
  (403), patchright headful under Xvfb with checkbox clicking (403 — cookie issued but never
  cleared; datacenter IP distrust), r.jina.ai (worked once for markdown, then 429 + IP-blocked
  for HTML), Wayback CDX (timeouts).
- **What worked: the z-ai `page_reader` function** — fetched every needed page as full HTML
  through Cloudflare. That became the verification channel for the whole analysis.
- Also confirmed: animekhor.xyz (official 2024 base URL) 301-redirects → animekhor.org.

## 3. Sources used

| Source | Value |
|---|---|
| aniyomiorg/aniyomi-extensions@42159cc `src/en/animekhor` + `lib-multisrc/animestream` | the removed official 2024 extension (theme + old hosters) — Apache-2.0 |
| yuzono/anime-extensions master | maintained fork: current animekhor + the whole 76-lib extractor suite — ported several verbatim |
| Live site via page_reader | verified EVERY selector/endpoint this extension ships |

Key discoveries vs the 2024 code:
1. **Domain moved** (.xyz → .org).
2. **Search broke upstream**: `?s=` now returns EPISODE posts (div.tt with empty ownText — the
   old code would crash/render empty). Our search maps episode posts → series URLs.
3. **Hosters changed**: fresh episodes use dailymotion/ok.ru/rumble/turbovid/upns/p2pstream/
   vidara/bysekoze/abyss — the old set (streamwish/ahvsh/sbsonic/d000d/animeabc) only survives
   on old episodes. We support BOTH sets.

## 4. What was built

Branch `ext/animekhor` (from main). `EXTENSIONS/animekhor/` full workspace:

- `DEV/` — independent Gradle project (copied anikoto scaffolding + stubs).
  - `AnimeKhor.kt` — AnimeHttpSource direct (AniKoto pattern): popular/latest/search+filters,
    details, episodes, tolerant per-mirror video dispatch via legacy `getVideoList(SEpisode)`
    (fork-compat + dead-embed resilience).
  - `AnimeKhorFilters.kt` — dynamic genres (218)/studios (119) parsed from the /anime/ form +
    static status/type/sub/order (all values verified live).
  - `extractors/` — PlaylistUtils-lite (HLS variants+subs), vendored JsUnpacker/Unbaser
    (dean-Edwards), and 8 hoster extractors: Dailymotion, Okru, Rumble, StreamWish, VidHide
    (ahvsh/sbsonic), Dood (d000d), Vidara (`POST /api/stream`), Abyss (`datas`→enc-dec.app).
  - Icons: official AnimeKhor logo (5 densities) from the aniyomiorg repo.
  - Metadata: name "AnimeKhor 180", applicationId …en.animekhor180, extVersionId 1,
    versionCode 1 → 16.1, nsfw false. **Debug-only build (no keystore yet).**
- CI: `Build AnimeKhor (debug)` step added to BOTH release.yml and build.yml (on this branch).
- Docs: EXTENSION.md, MEMORY/README.md, MEMORY/sites/site-analysis.md (full verified intel).

## 5. Verification gates passed

- tree-sitter Kotlin parse: 13/13 files OK (2 rounds — after fixing HttpUrl/URI host parsing,
  missing parallelCatchingFlatMapBlocking import, Okru param shadowing, frozen FILTER_LIST val).
- Stub API cross-check: SAnime constants (ON_HIATUS=6), SEpisode.scanlator, AnimeFilter
  Group/Select/CheckBox ctors, Video named-arg ctor, GET/POST signatures — all verified.
- Live end-to-end (via page_reader/python replay): catalog selectors, search mapping
  (2 slugs verified to 200), episode structure, mirror decode, dailymotion/ok.ru/vidara/abyss
  chains resolve to real media URLs.

## 6. Not verified (honest list)

- Nothing has run ON A DEVICE yet — the debug APK artifact is exactly for that.
- streamwish/vidhide/dood/rumble chains ported from upstream (blocked from sandbox); guard
  pages may still appear → graceful skip. Next iteration can add WebView fallback (AniKoto's
  WebViewFetcher) if the user reports those servers empty.
- SPA hosters (upns/p2pstream/bysekoze) deferred — research notes in site-analysis §9.

## 7. Stage summary

- ★ AnimeKhor 180 v16.1 (debug) built on branch `ext/animekhor`; CI artifact is the deliverable.
- ★ The CF-blocked sandbox does NOT block development: page_reader = verification channel;
  video hosters are reachable directly.
- ★ Search = episode→series mapping (novel, verified). Dead-embed tolerance is mandatory
  ("DPlayer: Video Not Available" options are normal on this site).
- Next session: user device feedback → fix cycle on the same branch (one change at a time);
  keystore + release pipeline only after the user approves.

---

## 8. Addendum — video-pipeline fix (same session, pre-artifact)

Self-review caught a critical bug in the first commit: only the LEGACY `getVideoList(episode)`
was implemented while `hosterListParse` returned empty — on modern Aniyomi (ext-lib 16) the app
calls **`getHosterList(episode)`** first and would have shown ZERO videos. Fixed (commit 8fe3326)
with the AniKoto dual-pipeline pattern:

- `getHosterList(episode)` — one `Hoster(hosterUrl=embed, hosterName=mirrorLabel, videoList=…)`
  per mirror that actually resolved; empty mirrors dropped entirely.
- `getVideoList(episode)` — flattens the hoster list (legacy forks).
- Sorting moved to the app-called `List<Video>.sortVideos()` override (preferred server → quality → resolution).

The v16.1-ak-test1 CI run (#29) built the broken code → cancelled; v16.1-ak-test2 (#30) builds the fix.
★ Lesson: on ext-lib 16, "implement only the legacy pipeline" is NOT a valid shortcut — the modern
pipeline is what current Aniyomi actually calls first. Always implement BOTH (or delegate one to the other).

---

## 9. Addendum 2 — build success (same session)

CI history (all runs on branch ext/animekhor, release.yml publish=false):

| Run | Tag | Result | Cause |
|---|---|---|---|
| #29 | v16.1-ak-test1 | cancelled | built the pre-getHosterList code (superseded) |
| #30 | v16.1-ak-test2 | FAILURE | 6 Kotlin compile errors (tree-sitter can't catch semantics) |
| #31 | v16.1-ak-test3 | FAILURE | 1 remaining error (PlaylistUtils.originOrNull return type) |
| #31+ | v16.1-ak-test4 (run 34775167587) | **SUCCESS** | artifact `test-build-apks-v16.1-ak-test4` |

Compile fixes (all committed bca27e3 + 7dce4d4):
- `String.orNull()` extension must accept nullable receiver (`String?.orNull()`).
- kotlinx-serialization imports (Json/jsonArray/jsonObject/jsonPrimitive) missing in
  VidHideExtractor + VidaraExtractor → cascaded "Cannot infer type parameter R" on runCatching.
- StreamWish `videosFromUrl` needed the lambda-param signature (not `prefix: String`) to match
  trailing-lambda call sites (same style as VidHide).
- PlaylistUtils: nullable `quality` chain needed `?: "source"` fallback;
  `originOrNull()` needed an elvis fallback for the String return type.

Artifact verified (APK_INFO.md): filename/package/versionName/extClass/label all correct,
8 extractors + PlaylistUtils + JsUnpacker + Filters + selectors present in dex, 5 icon densities.
SHA256 `9fd09877…f4a9`, 110,953 B.

**Deliverable: `aniyomi-en.animekhor180-v16.1-debug.apk` from Actions artifact
`test-build-apks-v16.1-ak-test4` — awaiting user device test. Branch stays UNMERGED.**

★ Lessons (compile without local SDK):
1. tree-sitter catches syntax, NOT types — budget for at least one CI compile round on a fresh
   extension; read the kotlinc `e:` lines (they name exact lines) and fix in batch.
2. When porting upstream extractor code, port the IMPORTS too — unresolved symbols cascade into
   misleading inference errors far from the real cause.
3. Extension-visible naming: `aniyomi-en.animekhor180-…` comes from `base.archivesName` — the
   "180" suffix in applicationIdSuffix does NOT appear there automatically; archivesName is set explicitly.
