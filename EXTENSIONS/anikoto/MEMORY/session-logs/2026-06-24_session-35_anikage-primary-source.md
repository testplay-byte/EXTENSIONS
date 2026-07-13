# Session 35 — Anikage.cc Primary Source + Source Titles + Always-On

> Date: 2026-06-24 · Session #: 35 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Fix the v16.19 issues per user feedback:
1. Episode thumbnails not accurate (Kitsu had sparse data for newer anime)
2. No episode descriptions showing (Kitsu was empty for many)
3. Remove settings toggles (always on — app has display toggles)
4. Show episode source title instead of just episode number

Used the user-provided EPISODE_DATA_ARCHITECTURE.md as reference.

## Root cause of v16.19 issues

Kitsu was the only source, and it has **sparse data for newer/airing anime**:
- Wistoria S2: 0/12 episodes had thumbnails or descriptions (empty placeholders)
- Smoking: not in Kitsu at all (no mapping)

The architecture doc recommended **Anikage.cc** (TheTVDB) as the PRIMARY source — it has
rich data for most anime including airing shows. Verified:
- AoT: 25/25 episodes with thumbnails + descriptions
- Wistoria: 11/11 with thumbnails, 11/11 with descriptions
- Smoking: 12/12 with thumbnails, 6/12 with descriptions
- Demon Slayer: 26/26 with thumbnails + descriptions

## What was implemented

### Rewrote `EpisodeMetadataFetcher.kt` — multi-source with priority

**Sources (priority order):**
1. **Anikage.cc** (TheTVDB) — PRIMARY: thumbnails + descriptions + titles
   - `GET https://anikage.cc/api/media/anime/{anilistId}/episodes` → JSON array
   - Requires AniList ID (looked up from MAL ID via AniList GraphQL)
   - Behind Cloudflare but accessible with desktop Chrome UA (verified from sandbox)
2. **Kitsu** — FALLBACK: thumbnails + descriptions for older anime
   - `GET https://kitsu.app/api/edge/mappings` → Kitsu ID
   - `GET https://kitsu.app/api/edge/anime/{kitsuId}/episodes` → episodes
3. **AniList banner** — thumbnail fallback when no episode thumbnail
4. **Anime cover** (from anikototv.to) — final thumbnail fallback

**Merge priority (per architecture doc):**
- Thumbnail: Anikage → Kitsu → AniList banner → anime cover → null
- Description: Anikage → Kitsu → null (NO placeholder — leave empty if no data)
- Title: Anikage → Kitsu → null (used to enrich SEpisode.name)

### Removed settings toggles (always on)

Per user: "dont give the option to turn off the episode thumbnail and episode summary it will stay on by default. as the app has the feature to hide it."

- Removed `PREF_EP_THUMBNAILS_KEY` + `PREF_EP_DESCRIPTIONS_KEY` and their SwitchPreferences
- The enrichment always runs
- The Aniyomi app's built-in per-anime toggles ("Show episode previews" / "Show episode summaries") control display

### Episode source titles

Per user: "show the episode source title too instead of the episode number"

- When Anikage/Kitsu provides a title, `SEpisode.name` is set to `"Episode {num} - {source title}"`
  (e.g., "Episode 1 - To You, in 2000 Years: The Fall of Shiganshina (1)")
- Falls back to the original anikototv.to title if no source title available
- This gives meaningful episode names instead of just "Episode 1"

### Descriptions — no placeholder

Per user: "leave the description empty or have a custom description like no description"

- `SEpisode.summary` is set to the Kitsu/Anikage description if available
- If no description available, it's left null (app shows simple layout — no "No description available." placeholder)
- This matches the user's instruction to leave it empty

## Verification

### Build verification (v16.20)
- BUILD SUCCESSFUL (after fixing MediaType deprecation: `toMediaTypeOrNull()`)
- All 11 checklist items pass: Stub! count=0, extClass=.Anikoto, versionId=11 STABLE
- DEX verified: EpisodeMetadataFetcher class in classes4.dex with all Anikage/AniList/Kitsu logic
- Settings toggles removed (0 occurrences of pref_ep_thumbnails/pref_ep_descriptions)
- MD5: `8967e9d8a72beaa3bcd776e240690070`

### Live verification (Anikage.cc from sandbox)
- ✅ AoT (MAL 16498): 25 episodes, EP1 title="To You, in 2000 Years...", thumbnail=YES, description=YES
- ✅ Wistoria (MAL 59983): 11 episodes, EP1 title="Barrier Day", thumbnail=YES, description=YES
- ✅ Smoking (MAL 62076): 12 episodes, EP1 title="Episode 1", thumbnail=YES, description=YES
- ✅ Demon Slayer (MAL 38000): 26 episodes, EP1 title="Cruelty", thumbnail=YES, description=YES

All 4 test anime now have rich data — a massive improvement over v16.19 (Kitsu-only).

## Files changed

- `metadata/EpisodeMetadataFetcher.kt` — completely rewritten: Anikage (primary) + Kitsu (fallback) + AniList (ID lookup + banner). Multi-source merge with priority.
- `Anikoto.kt`:
  - Removed 2 preference getters (epThumbnailsEnabled, epDescriptionsEnabled)
  - Removed 2 SwitchPreferences from setupPreferenceScreen
  - Removed 4 companion object constants
  - Updated `enrichEpisodesWithMetadata`: always runs, uses source titles, no description placeholder
- `build.gradle.kts` — versionCode 19→20 (versionId stays 11 STABLE)
- `AnikotoLog.kt` — EXTENSION_VERSION updated to v16.20

## Status

- ✅ Anikage.cc primary source (rich data for all tested anime, including airing shows)
- ✅ Kitsu fallback (for anime not in Anikage)
- ✅ Source titles in episode names ("Episode 1 - To You, in 2000 Years...")
- ✅ Descriptions (no placeholder — empty if no data)
- ✅ No settings toggles (always on — app has display toggles)
- ✅ v16.20 built + all 11 checklist items pass
- ⏳ User to test on device
