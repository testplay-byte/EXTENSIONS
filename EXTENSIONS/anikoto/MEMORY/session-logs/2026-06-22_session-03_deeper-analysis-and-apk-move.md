# Session 03 — Deeper Analysis (Network, Utils, Lib Extractors) + APK Move

> Date: 2026-06-22 · Session #: 03 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Two tasks from the user:
1. **Move** `APK/anikoto-by-1118000-v3.apk` → `APK/REFERENCE/` (separate it), and set up the
   APK-analysis documentation scaffolding — but do NOT analyze the APK yet (user will direct when/how).
2. **Do some more analysis and proper understandings** to round out the knowledge base before we
   start building. I chose the three highest-value remaining gaps (network layer, `keiyoushi.utils`
   core, key `lib/` extractors) because they directly enable writing extension code.

## What was done

### A. APK move + scaffolding
- Created `APK/REFERENCE/` and moved `anikoto-by-1118000-v3.apk` into it. Verified integrity
  (still a valid Android package, 12 zip entries, 255,830 bytes).
- Created `APK/REFERENCE/README.md` — rules (cross-check only, never copy), contents table,
  "analysis PENDING" status, how to add more reference APKs.
- Created `MEMORY/research/apk-reference/` folder + `README.md` defining the planned analysis
  structure (7 planned files: overview, manifest/source-class, site/flow, audio-types, video-extraction,
  logging, cross-check-lessons), the method (jadx/apktool/unzip/aapt — TBD by user), the rule
  (cross-check only, verify against live site, promote findings via two-tier memory), and what NOT
  to do. **No analysis performed** — per the user's instruction.

### B. Three parallel deep-analysis subagents (Tasks 4-A, 4-B, 4-C)
Dispatched three Explore agents in parallel, each appending to `/home/z/my-project/worklog.md` per
the Task-ID protocol:

- **Task 4-A (network-and-interceptors):** the ext-lib v16 network package (stubs) + the app's real
  `core/common/.../network/` impls + yuzono's `lib/` interceptor modules. Agent found a path
  correction (real impl is at `core/common/`, not `core/`).
- **Task 4-B (keiyoushi-utils-core):** all 13 files of `core/src/main/kotlin/keiyoushi/utils/` +
  `reactFlight/`. Documented every public API, verified the `Source.kt` legacy-override status
  against v16 source.
- **Task 4-C (key-lib-extractors-and-helpers):** `playlistutils`, `cryptoaes`, `m3u8server`,
  `universalextractor` + 3 host extractors (`filemoon`, `streamtape`, `vidmoly`).

### C. Verified the critical migration issue myself
Agent 4-C flagged that yuzono's `lib/` extractors use the ext-lib 14 `Video(url, quality, videoUrl, …)`
positional constructor. I verified this directly:
- `playlistutils/PlaylistUtils.kt:102-109` — `Video(playlistUrl, videoNameGen("Video"), playlistUrl, headers=…, …)` (legacy positional)
- `universalextractor/UniversalExtractor.kt:101` — `Video(resultUrl, "$prefix - $host: …", resultUrl, …)` (legacy positional)
- `ext-lib-aniyomiorg/.../Video.kt:69-89` — the legacy ctor is `@Deprecated(level = ERROR)` on v16

**Confirmed: yuzono's `lib/` modules will NOT compile against ext-lib v16 as-is.** Every `Video(...)`
construction must be rewritten to named-arg form (`Video(videoUrl = …, videoTitle = …, …)`), and
the `url` (page URL) argument dropped (v16 `Video` has no `url`/`videoPageUrl`). Documented in
`ext-lib/03-key-lib-extractors-and-helpers.md` §8.

### D. Wrote the documentation (3 new research/ext-lib files + 2 APK scaffolding files + 4 index updates)

New files:
- `MEMORY/research/04-network-layer-and-interceptors.md` — NetworkHelper (Cloudflare baked in),
  GET/POST/suspend shortcuts, await/awaitSuccess, JavaScriptEngine (QuickJS, per-call lifecycle),
  rateLimit/rateLimitHost (deprecated in v16 stub, still work), headers/headersBuilder patterns,
  + yuzono's 6 interceptor lib modules (incl. the `randomua` Spotless-check bug).
- `MEMORY/research/05-keiyoushi-utils-core.md` — all 13 core utils files: Json, Protobuf, GraphQL,
  Network, Coroutines (parallel helpers), Preferences (full API), Source (★ 2 legacy overrides
  MUST be deleted for v16 — verified), UrlUtils, Crypto, Date, Context, NextJs (RSC parser),
  Collections. ~86 public exports documented.
- `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` — playlistutils (HLS/DASH), cryptoaes
  (CryptoJS-compat), m3u8server (local proxy), universalextractor (WebView fallback), + 3 host
  extractors, + the ★ v16 Video-ctor migration flag (§8), + "which extractor for which host" table.
- `APK/REFERENCE/README.md` — APK folder rules + contents + PENDING status.
- `MEMORY/research/apk-reference/README.md` — planned analysis structure (PENDING user direction).

Index updates:
- `MEMORY/research/README.md` — added entries 4, 5, 6 (network, utils, apk-reference).
- `MEMORY/ext-lib/README.md` — added entry 3 (lib extractors) + 2 companion-doc links.
- `MEMORY/README.md` §7 — populated quick-links with all new docs + APK section.

## Key findings / decisions

1. **`network.client` already has Cloudflare baked in** — most sites don't need yuzono's
   `lib/cloudflareinterceptor`. Only use it if the built-in one is insufficient or for forks that
   stripped it.
2. **The canonical client-override pattern:** `override val client = network.client.newBuilder().addInterceptor(...).build()` — NEVER construct a fresh `OkHttpClient.Builder()` (you'd lose Cloudflare, UA, cache, DoH, cookies).
3. **`rateLimit`/`rateLimitHost` are deprecated in the v16 stub** (with `replaceWith = ReplaceWith("this")`)
   but the app's real impl still works. Future-proofing: roll your own `Interceptor` for long-term
   stability. Deprecation message: "default impl no longer provided... to prevent forks from bypassing it."
4. **`JavaScriptEngine` has no `close()`** — each `evaluate()` creates a fresh `QuickJs.create().use { }`
   (auto-closed). Per-call construction is fine.
5. **`keiyoushi.utils.Source` base class:** 2 of 12 legacy overrides (`videoListRequest(episode)`,
   `videoListParse(response)`) WON'T COMPILE on v16 — the v16 signatures take `Hoster`. Must delete
   them when adapting `core/Source.kt`. The other 10 still compile (functional guardrails).
6. **★ `lib/` extractors use the ext-lib 14 `Video(url, quality, videoUrl, …)` ctor** — `@Deprecated(level=ERROR)` on v16. **This is the single biggest mechanical change in porting yuzono libs to v16.** Every `Video(...)` construction must be rewritten to named-arg form. Affects `playlistutils`, `universalextractor`, `streamtapeextractor`, `m3u8server/M3u8Integration`, and every host extractor.
7. **`randomua` Spotless-check bug:** the build-logic `RandomUACheck` looks for `override fun getMangaUrl(` but the anime API method is `getAnimeUrl(`. The check never fires for anime extensions (0 `getMangaUrl` in `src/`). If we adopt `randomua`, we must manually override `getAnimeUrl()`.
8. **`coroutines.parallelCatching*` catches `Throwable` including `CancellationException`** — a known Kotlin coroutines anti-pattern. Yuzono uses them anyway; be aware.
9. **`Preferences.kt` is the largest core file (522 lines)** with the full pref-builder API (`addEditTextPreference`/`addListPreference`/`addSetPreference`/`addSwitchPreference` + `delegate` + `LazyMutable`). Every `ConfigurableAnimeSource` uses it.
10. **APK move done; analysis PENDING.** Scaffolding in place so the analysis is "properly handled" when the user directs.

## Files created / modified

New (7):
- `MEMORY/research/04-network-layer-and-interceptors.md`
- `MEMORY/research/05-keiyoushi-utils-core.md`
- `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md`
- `MEMORY/research/apk-reference/README.md`
- `APK/REFERENCE/README.md`
- `MEMORY/session-logs/2026-06-22_session-03_deeper-analysis-and-apk-move.md` — this log

Moved:
- `APK/anikoto-by-1118000-v3.apk` → `APK/REFERENCE/anikoto-by-1118000-v3.apk`

Modified (3 indexes):
- `MEMORY/README.md` §7 (quick-links expanded with all new docs + APK section)
- `MEMORY/research/README.md` (entries 4, 5, 6 added)
- `MEMORY/ext-lib/README.md` (entry 3 + 2 companion links added)

(Subagents appended to `worklog.md` per the Task-ID protocol — tasks 4-A, 4-B, 4-C.)

## Status at end of session

- ✅ APK moved to `APK/REFERENCE/` (integrity verified).
- ✅ APK analysis scaffolding in place (`APK/REFERENCE/README.md` + `MEMORY/research/apk-reference/README.md`); **NO analysis performed** per user instruction.
- ✅ Network layer + interceptors fully documented (`research/04`).
- ✅ `keiyoushi.utils` core module fully documented (`research/05`); `Source.kt` v16 migration verified.
- ✅ Key `lib/` extractors/helpers documented (`ext-lib/03`); ★ v16 Video-ctor migration issue verified + documented.
- ✅ All indexes updated; cross-references resolve.
- ⏳ Reference APK NOT analyzed (pending user direction).
- ⏳ `WORKSPACE/` not yet created (user's plan: DEV + workflow folders, next).
- ⏳ First real build NOT yet done (open verification items from session 02 still stand: JitPack serves v16, JDK 17 reads it, tapmoc compat, `core/Source.kt` legacy-override deletion, R8 keep rules).

## Next steps (for the next session)

Awaiting user guidance. Per their message: "after you have done these and verified ill tell you how to proceede with the next step." Likely candidates:
1. **Set up `WORKSPACE/`** with the layout from `guides/01-build-setup-for-ext-lib-16.md` (DEV + WORKFLOW folders per the user's earlier plan), adapting yuzono's build-logic for v16 (swap aniyomi-lib coordinate, `java = 17`, `versionName = "16."`, delete the 2 broken `Source.kt` overrides, port needed `lib/` modules with the Video-ctor fix).
2. **Verify the build** with a minimal stub extension (closes the open verification items).
3. **Analyze the reference APK** (when the user directs — using the structure in `research/apk-reference/README.md`).
4. **Pick the first target site** + run the site-analysis workflow → `MEMORY/sites/`.

## Open issues (still in TEMPORARY_MEMORY)

- None currently in `TEMPORARY_MEMORY/`. The open verification items (JitPack v16, JDK 17 build,
  tapmoc, `core/Source.kt` cleanup, R8 keep rules) and the newly-found `lib/` Video-ctor migration
  are all documented in the mature docs (`guides/01` §6, `research/03` §8, `research/05` §7.2,
  `ext-lib/03` §8). They'll be resolved at the first real build. If any fails, the resolution goes
  to `TEMPORARY_MEMORY/` first, then `issues-resolutions/` once verified.

## Honest notes

- **No code was written** — purely analysis + docs + APK move, per the user's instruction.
- **The "more analysis" scope was my choice.** The user said "some more analysis and proper
  understandings as they would help a lot" without specifying. I picked network/utils/lib-extractors
  because they're the three things every extension directly uses. I did NOT deep-dive the download
  pipeline (ffmpeg args), the full `AnimeExtensionLoader` flow, or the remaining 60+ `lib/` host
  extractors — those can be done on-demand when relevant to a target site.
- **The `lib/` Video-ctor migration issue** is the most consequential new finding. It means porting
  yuzono's `lib/` modules to v16 is NOT a simple copy — every `Video(...)` call needs rewriting.
  This is mechanical but pervasive (playlistutils alone has 3 call sites). Flagged prominently in
  `ext-lib/03` §8.
- **Agent 4-A found a path correction** I'd have missed: the app's real network impl is at
  `core/common/.../network/`, not `core/.../network/`. Good catch — incorporated into `research/04`.
- **All claims are sourced** (file paths + line numbers). The three subagent reports were
  cross-checked against my own direct reads for the critical claims (Video ctor, Source.kt overrides,
  rateLimit deprecation).
