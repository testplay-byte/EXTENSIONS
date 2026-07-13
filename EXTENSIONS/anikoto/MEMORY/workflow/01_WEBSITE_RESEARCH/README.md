# Step 01 — Website Research

> **Status: TEMPLATE.** Filled in when we build the first extension. This step is the foundation —
> every later step depends on accurate site analysis.

## Purpose (from spec)
- Log architectural details of target websites (HTML structures, API endpoints).
- Document available streaming servers, video qualities, and audio versions.
- Serve as a historical reference and stepping stone for onboarding new sites quickly.

## What belongs here
Per-extension site-research artifacts created during the build:
- `site-analysis.md` — full site analysis (URL structure, request flow, anti-bot measures).
- `endpoints.md` — endpoint inventory with example requests/responses (verified via agent-browser).
- `audio-types.md` — SUB/HSUB/DUB mapping (project rule §7 — verify which data-type serves which audio).
- `servers.md` — server-list paths (ALL of them tested, per rule §1 "test ALL servers from ALL endpoints").
- `video-flow.md` — embed → player → stream resolution chain.

> **Promotion rule:** once a site analysis is verified, the **generalizable** findings promote to
> `EXTENSIONS/anikoto/MEMORY/sites/` (per-site permanent record). This folder holds the **working notes +
> process** during the build; `EXTENSIONS/anikoto/MEMORY/sites/` holds the **verified permanent record**.

## How to do this step (process)
1. **Use a real browser (agent-browser)** — per rule §1, do NOT conclude from curl/API alone. Load
   the page, capture network requests, see what actually happens.
2. **Map the URL structure**: home, search, anime detail, episode list, watch/embed pages.
3. **Enumerate ALL server-list paths** — sites have multiple; test each one's full resolve chain.
4. **Confirm the 3 audio types** (SUB, HSUB, DUB — not 2) — verify which `data-type`/attribute serves
   which audio. Don't assume. (Rule §7.)
5. **OCR the webpage** if needed to confirm on-page labels.
6. **Document dedup behavior** — the site shares tokens across audio types (same video under different
   labels). Document the dedup strategy.
7. **Test video extraction** end-to-end: embed page → player JS → stream URL → plays.

## MEMORY cross-references
- `EXTENSIONS/anikoto/MEMORY/sites/README.md` — per-site analysis structure (the promotion target).
- `MEMORY/research/04-network-layer-and-interceptors.md` — how to make requests, headers, Cloudflare.
- `MEMORY/PROJECT_RULES.md` §1 (verify everything), §7 (audio types), §8 (episode display).

## Fill-in template (when building extension X)
```
01_WEBSITE_RESEARCH/
└── <EXTENSION_NAME>/              e.g. ANIMIX/
    ├── site-analysis.md           (URL structure, request flow, anti-bot)
    ├── endpoints.md               (endpoint inventory + example req/resp)
    ├── audio-types.md             (SUB/HSUB/DUB mapping — verified)
    ├── servers.md                 (all server-list paths tested)
    └── video-flow.md              (embed → player → stream chain)
```

## Status
Template only. Populated when the first extension's site is chosen.
