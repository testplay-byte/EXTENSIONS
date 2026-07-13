# FEATURES/ — Reusable Feature Implementation Guides

> **Dedicated folder for "how to implement a specific feature" guides.** Each guide is a complete,
> self-contained recipe for implementing one feature in ANY extension. These are NOT step-by-step
> build guides (those are in the parent folder, `01-05`). These are FEATURE guides — reusable
> building blocks you add to an extension once the core (catalog + details + episodes) is working.
>
> **When to use these:** after completing Steps 1-3 (catalog + details + episodes), pick the
> features you want from the index below and follow each guide. Each guide tells you exactly which
> files to create, what code to write, how to wire it in, and how to verify it works.

---

## Why a separate folder?

The build steps (`01-05`) are a **linear workflow** — every extension follows them in order. But
**features are optional and composable** — not every extension needs episode metadata enrichment,
or smart search, or a local proxy. Mixing feature guides with step guides makes both harder to
navigate.

This folder keeps feature guides separate so you can:
- **Browse available features** at a glance (the index below)
- **Implement only what you need** for each extension
- **Add new feature guides** without cluttering the step workflow
- **Reference a feature** from multiple extensions (the guides are site-agnostic)

---

## Feature index

| Feature | Guide | What it does | Status |
|---|---|---|---|
| **Episode metadata enrichment** | [`episode-metadata-enrichment.md`](episode-metadata-enrichment.md) | Adds episode thumbnails + titles + descriptions via Jikan + AniList + Anikage + Kitsu multi-source fetch | ✅ implemented in AniKoto + AnimePahe |
| **Multi-season episode renumbering** | [`multi-season-episode-renumbering.md`](multi-season-episode-renumbering.md) | Detects when a site continues episode numbering across seasons (e.g. Season 2 starts at ep 13) and renumbers starting from 1 | ✅ implemented in AnimePahe |
| **Video playback (Kwik HLS)** | [`video-playback-kwik-hls.md`](video-playback-kwik-hls.md) | Extracts m3u8 HLS streams from Kwik (kwik.cx) embed links via packed-JS unpacking | ✅ implemented in AnimePahe |

### Planned features (add guides as they're implemented)

| Feature | What it does | Will be implemented in |
|---|---|---|
| Video playback (Kwik-style extractor) | Server discovery + video extraction + WebView WAF bypass | Step 4 (animepahe) |
| Local proxy server (PNG-wrapped streams) | Strips PNG headers from disguised streams | Step 4 (if needed) |
| Smart search (AI-powered) | Resolves descriptive queries via Google AI Search | AniKoto has it; optional for others |
| Episode metadata fallback (title-search) | Jikan title-search when no MAL ID link on detail page | When a site lacks MAL external links |
| Fork compatibility (EpisodeMeta encoding) | `/watch/<slug>/ep-N#fragment` encoding for legacy forks | All extensions (see Step 3) |

---

## How to write a new feature guide

When you implement a new feature for the first time, **write a guide for it here** so future
extensions can reuse the pattern. Use this structure:

```markdown
# <Feature Name> — Implementation Guide

> What the feature does + when to use it.

## What this feature does
<1-2 paragraph overview>

## Required files
<list of files to create/modify>

## Implementation steps
1. <step 1 — which file, what code>
2. <step 2 — ...>
...

## ★ Conventions (CRITICAL — copy these exactly)
<table of exact wording / formats / patterns that MUST match across extensions>

## Common issues + fixes
<symptom → cause → fix for known pitfalls>

## Verification
<how to test it works>

## Reference implementations
<which extensions have this feature + their file paths>
```

### Guidelines

1. **Be site-agnostic.** The guide should work for ANY extension, not just one site. Use
   `<Name>` / `<name>` placeholders, not hardcoded site names.
2. **Document conventions explicitly.** If there's an exact wording (like "EP N - title" or
   "Fetching preview images from external sources"), put it in a table and say "copy these
   verbatim". Future extensions MUST match.
3. **Explain WHY, not just WHAT.** If a pattern is non-obvious (like OkHttp-first vs
   WebView-first), explain the root cause so the implementer understands when to deviate.
4. **Include common issues.** Every pitfall you hit should be documented so the next extension
   avoids it.
5. **Link to real implementations.** Point to the actual `.kt` files in `EXTENSIONS/<name>/`
   so the implementer can read working code.
6. **Update the index above** when you add a guide.

---

## Convention: user-facing text

★ **General rule for ALL feature guides:** user-facing text (settings summaries, toast messages,
error messages) should NEVER expose implementation details (API names, internal source names,
technical jargon). Use generic terms like "external sources" instead of naming specific APIs.

This keeps the UI clean and lets you swap implementations without changing user-visible text.

| ✅ Good | ❌ Bad |
|---|---|
| `Fetching preview images from external sources` | `Fetching preview images from MAL / AniList / Kitsu` |
| `Fetching episode titles from external sources` | `Fetching titles from Jikan (MyAnimeList)` |
| `Video extraction in progress` | `Kwik extractor resolving CDN token` |
| `Server unavailable` | `Cloudflare blocked the WebView fetch` |

---

## Reference: which extensions have which features

| Extension | Metadata enrichment | Multi-season renumbering | Video playback | Smart search | Local proxy |
|---|---|---|---|---|---|
| **AniKoto 180** | ✅ (WebView-first, light Cloudflare) | ❌ (not needed — site starts at 1) | ✅ (4 servers + Kiwi) | ✅ | ✅ (PNG-wrapped) |
| **AnimePahe 180** | ✅ (OkHttp-first, hard Cloudflare) | ✅ (site continues numbering) | ✅ (Kwik HLS via JsUnpacker) | ❌ | ❌ |
| **MKissa 180** | ✅ (AniList ID + Anikage, OkHttp-only) | ❌ (not needed) | 🚧 (4 servers — in progress) | ❌ | ❌ |

When implementing a feature, check which extensions already have it and read their code for
reference — but follow the guide (not the code blindly) since the guide captures the GENERAL
pattern.
