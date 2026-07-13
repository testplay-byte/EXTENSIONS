# ext-lib/ — ext-lib 16 Feature Inventory & Notes

> **Status: VERIFIED.** What the extension library (ext-lib 16 era) provides: extractors, helpers,
> multisrc themes, and the APIs we build on. Promote here only after confirming against the
> reference repo source in `SHARED/REFERENCE_HUB/anime-extensions-ref/lib/` and `lib-multisrc/`.

## Where to look in the reference repo

- `SHARED/REFERENCE_HUB/anime-extensions-ref/lib/` — individual extractor & helper modules (one folder per
  capability). Examples observed during setup: `playlistutils`, `m3u8server`, `cryptoaes`,
  `lzstring`, `unpacker`, `cloudflareinterceptor`, `cookieinterceptor`, `randomua`, `i18n`,
  `universalextractor`, plus many host-specific extractors (`filemoonextractor`, `streamtapeextractor`,
  `vidmolyextractor`, `voeextractor`, `mixdropextractor`, `doodextractor`, …).
- `SHARED/REFERENCE_HUB/anime-extensions-ref/lib-multisrc/` — theme multisrc templates: `anilist`,
  `animekaitheme`, `animestream`, `datalifeengine`, `dooplay`, `dopeflix`, `pelisplus`, `wcotheme`,
  `yflixtheme`, `zorotheme`.
- `SHARED/REFERENCE_HUB/anime-extensions-ref/core/` — shared core module.
- `SHARED/REFERENCE_HUB/anime-extensions-ref/common/` — shared common code.
- `SHARED/REFERENCE_HUB/anime-extensions-ref/template/` — the per-extension template (`README-TEMPLATE.md`).
- `SHARED/REFERENCE_HUB/anime-extensions-ref/src/<lang>/` — concrete extension examples per language.

## What to document here (once verified)

- For each extractor/helper we plan to use: name, purpose, inputs/outputs, key methods, gotchas.
- The `Video` / `VideoResolver` / `Server` data model and how a multisrc theme wires episodes → servers → videos.
- How a new extension module is declared (gradle, `AndroidManifest.xml`, `PreferenceScreen`, source class).
- ext-lib versioning & compatibility notes (16.x specifics vs older).
- Differences between this reference repo's approach and the latest ext-lib (the reference is "old",
  so flag anything outdated).

## Naming

`YYYY-MM-DD_<topic>.md` (e.g. `2026-06-23_playlistutils-usage.md`).

## Entry checklist

- [ ] Claims cite specific file paths in `SHARED/REFERENCE_HUB/anime-extensions-ref/`.
- [ ] Method signatures copied from source, not memory.
- [ ] `Last updated:` date present.

## Current contents (read in this order)

1. **`01-ext-lib-16-source-and-versioning.md`** — ★ START HERE. Which repo/tag has ext-lib 16
   features, why the komikku fork (yuzono's pin) is stale, how to depend on `v16`, version history,
   verification commands.
2. **`02-ext-lib-16-api-reference.md`** — the authoritative compile-time API: `AnimeSource`,
   `AnimeCatalogueSource`, `AnimeHttpSource` (all ext-lib 16 methods), `Hoster`, `Video`, `Track`,
   `TimeStamp`, `FetchType`, `SAnime`, `SEpisode`, `ParsedAnimeHttpSource` (deprecated),
   `ResolvableAnimeSource`, `ConfigurableAnimeSource`, `AnimeSourceFactory`. Includes the
   "what to implement" checklist.
3. **`03-key-lib-extractors-and-helpers.md`** — yuzono's reusable `lib/` building blocks:
   `playlistutils` (HLS/DASH → `List<Video>`, the most-used helper), `cryptoaes` (CryptoJS-compat AES),
   `m3u8server` (local NanoHTTPD proxy for segment header injection / fake-header stripping),
   `universalextractor` (WebView fallback), + 3 host extractors (`filemoon`, `streamtape`, `vidmoly`).
   ★ **Critical migration flag:** these libs use the ext-lib 14 `Video(url, quality, videoUrl, …)`
   ctor which is `@Deprecated(level=ERROR)` on v16 — won't compile as-is. §8 documents the fix.

Companion docs in other folders:
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md` — how the app's mpv player consumes your
  Video/Hoster objects at runtime (which fields are read).
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` — exact diff between the published v16
  stub lib and the app's runtime source-api (what you can/can't use at compile time).
- `MEMORY/research/04-network-layer-and-interceptors.md` — the network layer (`NetworkHelper`, `client`, `headers`, interceptors) that the lib/ extractors use.
- `MEMORY/research/05-keiyoushi-utils-core.md` — `keiyoushi.utils` (`parseAs`, `useAsJsoup`, `UrlUtils`, `parallelCatchingFlatMapBlocking`, `Source` base class) that the lib/ extractors use.
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` — the Gradle build config implementing v16.
- `MEMORY/guides/02-how-to-create-a-new-extension.md` — per-extension file layout + skeleton.
- `MEMORY/decisions/01-use-aniyomiorg-extensions-lib-v16.md` — ADR for this dependency choice.
