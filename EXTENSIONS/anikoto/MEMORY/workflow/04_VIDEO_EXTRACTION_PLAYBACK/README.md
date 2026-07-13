# Step 04 — Video Extraction & Playback

> **Status: TEMPLATE.** Filled in when we build the first extension. This is the **core objective** —
> seamless communication between target streaming sources and the internal video player.

## Purpose (from spec)
- **Core Objective:** ensure seamless communication between the target streaming sources and the
  internal video player.
- **Local Testing:** run localized Python scripts to isolate HTTP responses, dissect site payloads,
  and optimize formatting **prior to app integration**.
- **Dynamic Handling:** manage seamless runtime switching between video servers, quality profiles
  (e.g. 720p, 1080p), audio tracks, and subtitles.

## What belongs here
- `hoster-pipeline-impl.md` — `hosterListParse` + `videoListParse(response, hoster)` + (optional)
  `resolveVideo` implementation notes.
- `python-prototypes/` — **local Python scripts** that isolate HTTP responses and dissect payloads
  BEFORE porting to Kotlin. One script per hoster/flow. This is the spec's "local testing" step.
- `extractor-choices.md` — which `lib/` extractors used for which hoster (the "which extractor for
  which host" table from `MEMORY/ext-lib/03-...md` §7, filled for this site).
- `server-quality-switching.md` — how `sortHosters()` / `sortVideos()` implement runtime switching
  per user prefs (server, quality, audio).
- `subtitles-audio.md` — subtitle + audio-track handling (which `Video.subtitleTracks` /
  `audioTracks` get populated, how).
- `dedup-strategy.md` — how SUB/HSUB/DUB token-sharing dedup works for this site (rule §7).

## How to do this step (process)
1. **From step 01's `video-flow.md`**: for each server/embed the site offers, trace the full chain:
   embed page → player JS → API call → stream URL.
2. **Prototype in Python FIRST** (per spec): write a script per hoster that replicates the request
   chain, prints the resolved stream URL + headers. This isolates the HTTP logic from the Android
   build cycle → much faster iteration. Put scripts in `python-prototypes/`.
3. **Port to Kotlin**: for each hoster, decide — use an existing `lib/` extractor (e.g.
   `:lib:playlistutils` for HLS, `:lib:filemoonextractor` for Filemoon) OR write a custom extractor
   in the extension's `extractors/` package. Remember the v16 Video-ctor port (named args only).
4. **Implement `hosterListParse`**: parse the episode's watch page → `List<Hoster>`. First hoster =
   the one you want auto-selected (after `sortHosters`). Set `lazy = true` for expensive hosters.
5. **Implement `videoListParse(response, hoster)`**: fetch videos for one hoster. ★ Label each
   `Video.videoTitle` with audio type (`"DUB - 1080p"`, `"SUB - 720p"`). ★ Set `preferred = true`
   on exactly ONE (best/user-preferred). ★ Set `Video.headers` with Referer if the host requires it.
   ★ Dedup across SUB/HSUB/DUB (rule §7).
6. **Implement `resolveVideo`** ONLY if URLs need per-play resolution (expiring tokens). Else leave
   default (no-op).
7. **Implement `sortHosters()` / `sortVideos()`** for user-preference ordering (read from prefs set
   in step 05). Use `it.videoTitle.contains(...)`, NOT `it.quality` (doesn't exist on v16).
8. **Verify**: build → install → test each hoster end-to-end. Check logs in `Download/1118000/`.

## MEMORY cross-references
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md` — ★ the 3-stage pipeline, what the player reads.
- `MEMORY/ext-lib/02-...api-reference.md` §4.3 (hoster pipeline methods), §5 (Hoster), §6 (Video).
- `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` — reusable extractors + ★ v16 Video-ctor port.
- `MEMORY/research/04-network-layer-and-interceptors.md` — `awaitSuccess`, `useAsJsoup`, headers.
- `MEMORY/research/05-keiyoushi-utils-core.md` §5 (parallelCatchingFlatMapBlocking for multi-hoster).
- `MEMORY/PROJECT_RULES.md` §7 (3 audio types + dedup), §8 (scanlator for sub/dub at episode level).

## Fill-in template
```
04_VIDEO_EXTRACTION_PLAYBACK/
└── <EXTENSION_NAME>/
    ├── hoster-pipeline-impl.md
    ├── python-prototypes/
    │   ├── hoster-vidmoly.py
    │   ├── hoster-filemoon.py
    │   └── ...
    ├── extractor-choices.md
    ├── server-quality-switching.md
    ├── subtitles-audio.md
    └── dedup-strategy.md
```

## Python-prototype convention (per spec)
Each script in `python-prototypes/` should:
- Reproduce ONE hoster's request chain (embed URL → ... → stream URL).
- Print the resolved stream URL + required headers + any decrypted payloads.
- Use `requests` + `beautifulsoup4` (or `httpx`). Keep deps minimal.
- Be runnable standalone: `python3 python-prototypes/hoster-vidmoly.py <embed-url>`.
- Once the Kotlin port is verified to produce identical output, the script is kept as a regression
  reference (don't delete — useful when the site changes and you need to re-reverse-engineer).

## Status
Template only. Populated when the first extension's video extraction is built.
