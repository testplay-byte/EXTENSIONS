# Research: Episode Preview Images & Descriptions

> Date: 2026-06-24 · Session #: 33 · Status: RESEARCH ONLY (no implementation)

## How Aniyomi expects the data

### The SEpisode model
```kotlin
interface SEpisode {
    var url: String
    var name: String
    var date_upload: Long
    var episode_number: Float
    var fillermark: Boolean
    var scanlator: String?
    var summary: String?        // ← episode description (NOT currently set)
    var preview_url: String?    // ← episode thumbnail URL (NOT currently set)
}
```

We currently set: `url`, `name`, `episode_number`, `scanlator`, `date_upload`.
We do NOT set: `summary`, `preview_url`, `fillermark`.

### How the app renders episodes (`AnimeEpisodeListItem.kt`)

The app has **two render modes** based on whether `preview_url` and `summary` are set:

1. **Simple mode** (BOTH null): compact single-line layout — what we have now
2. **Full mode** (EITHER non-null): thumbnail + title + summary + metadata
   - `EpisodeThumbnail`: renders at 40% screen width (max 250dp), uses Coil image loader
   - `EpisodeSummary`: 3-line collapsible text (10sp, expandable on click)

### ★ The app ALREADY has toggle settings (no extension work needed)

Aniyomi has built-in per-anime settings (in the anime detail screen's overflow menu):
- **"Show episode previews"** → `showEpisodePreviews(flag)` — controls thumbnail display
- **"Show episode summaries"** → `showEpisodeSummaries(flag)` — controls description display

These are stored in `LibraryPreferences` and work regardless of what the extension provides.
If the user turns off "show previews" but the extension sets `preview_url`, the app hides it.
If the extension doesn't set `preview_url`, the toggle has no effect (nothing to show).

**Implication**: the user's "turn on/off in settings" request can be satisfied TWO ways:
1. **App-side** (already exists): the Aniyomi toggles control display
2. **Extension-side** (we'd add): controls whether we FETCH the data (saves bandwidth/latency)

## What anikototv.to provides

Tested the episode list API (`/ajax/episode/list/{animeId}`) and the anime detail page:
- **Episode list HTML attributes**: `data-id`, `data-num`, `data-slug`, `data-mal`, `data-timestamp`, `data-sub`, `data-dub`, `data-ids`
- **NO episode thumbnails** (no `<img>` tags, no image URLs in episode HTML)
- **NO episode descriptions** (the `<li title="...">` just contains the episode name, not a description)
- The `data-mal` attribute gives us the **MAL ID** — can be used to query external APIs
- The anime detail page has poster images (`cdn.anipixcdn.co/thumbnail/{hash}.jpg`) but these are the ANIME cover, not per-episode

## What external APIs provide

### Jikan (unofficial MAL API) — `GET https://api.jikan.moe/v4/anime/{malId}/episodes`
- ✅ Episode titles (English, Japanese, Romaji) — better than "Episode 1"
- ✅ Aired dates
- ✅ Filler/recap flags
- ✅ Scores
- ❌ NO thumbnails (0/11 populated for test anime)
- ❌ NO descriptions/synopsis
- Rate limit: 3 requests/second (need to handle)
- Tested with Wistoria (MAL 59983): returned 11 episodes with titles

### AniList GraphQL API
- ❌ The `Episode` type was REMOVED from the API (returns 400 error)
- `Media.episodes` is just a count (Int), not a list
- No per-episode data available at all

### Other Aniyomi extensions
- **NONE** of the reference extensions (yuzono/anime-extensions: HiAnime, AniWave, Miruro, etc.) set `preview_url` or `summary` on SEpisode
- Episode thumbnails and descriptions are virtually unused in the Aniyomi ecosystem
- No standard pattern to follow

## Implementation options (for when we implement)

### Option A: Jikan for descriptions (easy, partial)
- We have `data-mal` on each episode
- Batch-fetch from Jikan: `GET https://api.jikan.moe/v4/anime/{malId}/episodes`
- Match by episode number
- Set `summary` = MAL episode title + aired date
- **Pros**: easy, real data, improves episode names too
- **Cons**: no thumbnails, adds ~1 API call per anime (latency), Jikan rate limiting
- **Latency**: +0.5-1s per episode list load (one Jikan call)

### Option B: Generate thumbnails from video (complex, full)
- In `getHosterList` (which already resolves the video), fetch the first segment
- Use Android's `MediaMetadataRetriever.getFrameAtTime(0)` to extract a frame
- Save bitmap to cache, serve via local proxy or file:// URL
- **Pros**: real episode-specific thumbnails
- **Cons**: complex, slow (adds 2-5s to first play), memory-intensive, only works after video is resolved (not at episode list time)
- **Problem**: `getEpisodeList` (where `preview_url` is set) runs BEFORE `getHosterList` (where the video URL is known). Can't generate thumbnails at episode list time without an extra video resolve per episode.

### Option C: Anime cover as thumbnail (trivial, uniform)
- Set `preview_url` = anime's `thumbnail_url` for ALL episodes
- **Pros**: trivial to implement, all episodes show the anime cover
- **Cons**: all episodes look the same (not useful for identifying episodes)

### Option D: Jikan descriptions + anime cover thumbnails (recommended)
- Combine A + C: Jikan for `summary`, anime cover for `preview_url`
- **Pros**: both fields populated, reasonable effort, app shows full layout
- **Cons**: thumbnails aren't episode-specific, adds 1 API call
- This is the best balance of effort vs. value

## Settings design (for when we implement)

### Extension-side preferences (new, in our settings screen)
```
☑ Fetch episode descriptions (from MyAnimeList)
   Adds episode titles and aired dates. Adds ~1s to episode list loading.
☑ Show episode preview thumbnails
   Uses the anime cover image as the episode thumbnail.
```

- `pref_ep_descriptions` (Boolean, default: true) — controls Jikan API calls
- `pref_ep_thumbnails` (Boolean, default: true) — controls whether `preview_url` is set

### App-side settings (already exist)
- "Show episode previews" — per-anime toggle in overflow menu
- "Show episode summaries" — per-anime toggle in overflow menu
- These work independently of our extension settings

## Data flow (for when we implement)

```
getEpisodeList(anime):
  1. Fetch episode list from anikototv.to (existing)
  2. If pref_ep_descriptions is ON:
     a. Fetch from Jikan: GET https://api.jikan.moe/v4/anime/{malId}/episodes
     b. Match episodes by number
     c. Set SEpisode.summary = "{MAL title}\nAired: {date}"
     d. Optionally set SEpisode.name = "{epNum} - {MAL title}" (better names)
  3. If pref_ep_thumbnails is ON:
     a. Set SEpisode.preview_url = anime.thumbnail_url (the cover image)
  4. Return episodes
```

## Key findings summary

1. **The SEpisode model supports it** (`preview_url` + `summary` fields exist)
2. **The Aniyomi app renders it** (AnimeEpisodeListItem.kt has full thumbnail + summary layout)
3. **The app already has toggle settings** (showEpisodePreviews/showEpisodeSummaries)
4. **anikototv.to does NOT provide episode thumbnails or descriptions**
5. **Jikan (MAL API) provides episode titles + dates but NOT thumbnails/descriptions**
6. **AniList does NOT provide per-episode data** (Episode type was removed)
7. **No reference extension sets these fields** — no standard pattern to follow
8. **Best approach**: Jikan for descriptions (real data) + anime cover for thumbnails (uniform but present)
9. **Two levels of settings**: extension-side (fetch on/off) + app-side (display on/off, already exists)
