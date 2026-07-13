# Step 03 — Catalog & Episodes Management

> **Status: TEMPLATE.** Filled in when we build the first extension.

## Purpose (from spec)
- Implement extension-level UIs for Popular lists, Latest Releases, Search functionality, and
  advanced Filtering.
- Manage the Anime Details views, mapping out episode lists, metadata, and explicit audio tracks
  (e.g., Sub/Dub markers, H-Sub, etc.).

## What belongs here
- `catalog-impl.md` — the `popularAnimeRequest/Parse`, `searchAnimeRequest/Parse`,
  `latestUpdatesRequest/Parse`, `getFilterList` implementation notes + selectors/endpoints.
- `details-impl.md` — `animeDetailsParse` mapping (which page fields → which `SAnime` fields).
- `episodes-impl.md` — `episodeListParse` mapping + the **sub/dub availability strategy** (rule §8:
  use `SEpisode.scanlator`, NOT the episode name).
- `filters-impl.md` — the `AnimeFilterList` design (genres, status, sort, audio-type filter).

## How to do this step (process)
1. **Popular**: find the site's popular/trending page. Implement `popularAnimeRequest` +
   `popularAnimeParse` (Jsoup selectors or JSON parse).
2. **Latest**: find the latest-releases page/route. If the site has none, `supportsLatest = false`
   and throw `UnsupportedOperationException` in the `latestUpdates*` methods.
3. **Search**: find the search endpoint (GET query param? POST body? GraphQL?). Implement
   `searchAnimeRequest` + `searchAnimeParse`.
4. **Filters**: design `getFilterList` — genres, status, sort, audio-type (SUB/HSUB/DUB), etc.
5. **Details**: implement `animeDetailsParse` — map page fields to `SAnime` (`title`, `description`,
   `genre`, `status`, `thumbnail_url`, `update_strategy`, `fetch_type = FetchType.Episodes`).
6. **Episodes**: implement `episodeListParse`. ★ **Sub/dub availability goes in `SEpisode.scanlator`**
   (renders below episode name in UI), NOT in `name`. Keep `name` clean (number + title only).
   Aggregate per-episode SUB/HSUB/DUB availability into `scanlator` (e.g. `"SUB • HSUB • DUB"`).
7. **Verify each** with agent-browser against the live site before moving on.

## MEMORY cross-references
- `MEMORY/ext-lib/02-...api-reference.md` §4 (AnimeHttpSource abstract methods), §8 (SAnime),
  §9 (SEpisode + scanlator), §10 (FetchType), §11 (ParsedAnimeHttpSource — deprecated, use AnimeHttpSource).
- `MEMORY/research/05-keiyoushi-utils-core.md` §4 (Network helpers: `useAsJsoup`, `parseAs`),
  §5 (Coroutines: `parallelCatchingFlatMapBlocking`), §8 (UrlUtils).
- `MEMORY/PROJECT_RULES.md` §7 (3 audio types), §8 (scanlator for sub/dub).
- `MEMORY/guides/02-how-to-create-a-new-extension.md` §5 (SAnime/SEpisode filling).

## Fill-in template
```
03_CATALOG_EPISODES_MANAGEMENT/
└── <EXTENSION_NAME>/
    ├── catalog-impl.md
    ├── details-impl.md
    ├── episodes-impl.md
    └── filters-impl.md
```

## Status
Template only. Populated when the first extension's catalog/episodes are implemented.
