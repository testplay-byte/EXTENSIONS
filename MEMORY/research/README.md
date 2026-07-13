# research/ — Verified Research Findings

> **Status: VERIFIED.** Content here has been confirmed against source code, the live site, or a
> successful build. Cite the source for every claim.

## What goes here

- How Aniyomi works internally (app architecture, source loading, extension lifecycle).
- The `source-api` layout and the interfaces an extension must implement
  (`AnimeSource`, `AnimeCatalogueSource`, `Video`, `Track`, preferences, etc.).
- How the Aniyomi video player works and what it expects from a `Video` / `VideoResolver`.
- ext-lib 16 feature inventory (what extractors/helpers are available, what they do).
- Site behavior analysis (URL structure, request flow, server-list endpoints, anti-bot measures).
- Anything factual about the Aniyomi/extension ecosystem that future sessions need to know.

## What does NOT go here

- How-to steps → `guides/`.
- "Why we chose X" → `decisions/`.
- Issue + fix pairs → `issues-resolutions/`.
- Raw/unverified notes → `TEMPORARY_MEMORY/`.

## Entry checklist (before promoting a file here)

- [ ] Every claim cites a source (file path in `SHARED/REFERENCE_HUB/`, URL, commit, API endpoint).
- [ ] Facts vs. hypotheses are labeled (`VERIFIED:` vs `NOTE:`).
- [ ] File has a `Last updated:` date.
- [ ] The original temp note (if any) has been **deleted**.

## Naming

`YYYY-MM-DD_short-kebab-case-title.md`

## Current contents (read in this order)

1. **`01-aniyomi-video-pipeline-and-player.md`** — how the app's **mpv-based** player consumes
   `Video`/`Hoster` objects at runtime. The three-stage ext-lib 16 pipeline
   (`getHosterList` → `getVideoList(hoster)` → `resolveVideo`), new-vs-legacy flow detection, full
   resolution flow diagram, which `Video` fields the player actually reads, auto-selection +
   failure-fallback logic, subtitle/audio/timestamp wiring.
2. **`02-reference-extension-build-and-structure.md`** — the yuzono reference repo's build system:
   `settings.gradle.kts`, `libs.versions.toml`/`kei.versions.toml`, the 5 build-logic convention
   plugins (`PluginAndroidBase`/`PluginExtensionLegacy`/`PluginLibrary`/`PluginMultiSrc`/`PluginSpotless`),
   the `core/` utils module, shared manifest, a concrete extension anatomy (`src/en/tokuzilla/`),
   multisrc theme anatomy (`zorotheme`), the 73 `lib/` helper modules, versioning scheme, and the
   "to create a new extension" checklist.
3. **`03-compile-time-vs-runtime-discrepancy.md`** — ★ critical. Exact field-by-field diff between
   the **published** `aniyomiorg/extensions-lib:v16` stub lib (what you compile against) and the
   app's **runtime** `source-api/` (fuller). What you can/can't use in extension code. Also covers
   the verified extension-loader version-compat rule (`LIB_VERSION_MIN=12`, `LIB_VERSION_MAX=16`,
   `libVersion = versionName.substringBeforeLast('.').toDouble()`) and the Java 17 bytecode
   requirement.
4. **`04-network-layer-and-interceptors.md`** — `NetworkHelper` (shared OkHttpClient with Cloudflare
   baked in), `GET`/`POST`/suspend `get`/`post` shortcuts, `await`/`awaitSuccess`, `JavaScriptEngine`
   (QuickJS), `rateLimit`/`rateLimitHost` (deprecated in v16 stub, still work), `headers`/`headersBuilder()`
   patterns, and yuzono's `lib/` interceptor modules (`cloudflareinterceptor`, `cookieinterceptor`,
   `randomua` + its Spotless-check bug, `textinterceptor`, `synchrony`, `zipinterceptor`).
5. **`05-keiyoushi-utils-core.md`** — the standard toolkit every extension uses: `Json.kt`, `Protobuf.kt`,
   `GraphQL.kt`, `Network.kt`, `Coroutines.kt` (parallel helpers), `Preferences.kt` (full pref API),
   `Source.kt` (★ abstract base class — 2 legacy overrides MUST be deleted for v16), `UrlUtils.kt`,
   `Crypto.kt`, `Date.kt`, `Context.kt`, `NextJs.kt` (RSC parser), `Collections.kt`.
6. **`apk-reference/`** — subfolder for the reference APK analysis (**PENDING** user direction).
   See its README for the planned structure. Rule: cross-check only, never copy.
