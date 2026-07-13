# Session 37 — Fix @SerialName("Media") + Multi-Source Thumbnails + EP Title Format

> Date: 2026-06-24 · Session #: 37 · Duration: ~short · Timezone: America/Los_Angeles

## Root cause of v16.21 failure

The user's log showed `AniList ID for malId=57334 → null` for ALL anime, even though the
WebView `postJson` succeeded (DONE in 460ms). The AniList API returned valid JSON:
```json
{"data":{"Media":{"id":171018,"bannerImage":"..."}}}
```

But the Kotlin DTO had `val media: AniListMedia?` (lowercase `media`). kotlinx.serialization
is **case-sensitive** by default — `media` ≠ `Media`. The field never matched, so `media`
was always null → `id` was null → AniList ID was null → Anikage was never called → only Kitsu
fallback (which had sparse data) → fallback to anime banner for all episodes.

## Fixes (v16.22)

### Fix 1: @SerialName("Media") — THE root cause

Added `@SerialName("Media")` to the `media` field in `AniListMediaData`:
```kotlin
@Serializable
private data class AniListMediaData(
    @SerialName("Media") val media: AniListMedia? = null,  // ← was missing @SerialName
)
```

This was the one-line fix that makes everything work. AniList ID will now be found → Anikage
will be called → thumbnails + descriptions + titles will be fetched.

### Fix 2: AniList streamingEpisodes as thumbnail source

Per the user's multi-source priority request:
```
Thumbnail: Anikage → AniList → Kitsu → banner
```

Added `streamingEpisodes` to the AniList GraphQL query (fetched in the same request as the
AniList ID + banner — no extra API call):
```graphql
query { Media(idMal: $malId, type: ANIME) {
    id bannerImage streamingEpisodes { title thumbnail }
} }
```

AniList's `streamingEpisodes` is Crunchyroll-synced data. Each entry has a `thumbnail` URL
pointing to Crunchyroll's CDN. Episodes are ordered (index + 1 = episode number).

Updated `mergeEpisodes` to include AniList as a thumbnail source:
- Thumbnail: Anikage → AniList streamingEpisodes → Kitsu → banner → anime cover
- Title: Anikage → Kitsu (AniList streamingEpisodes titles are often slug-based, skipped)
- Synopsis: Anikage → Kitsu (AniList doesn't have descriptions)

### Fix 3: EP title format

Changed from "Episode 5 - title" to "EP 5 - title" per user request:
```kotlin
ep.name = "EP $epNum - $sourceTitle"
```

### Fix 4: Optimized AniList banner fetch

The banner is now fetched in the same query as the AniList ID (the `fetchAniListId` method
caches both the streamingEpisodes and the banner). The separate `fetchAniListBanner` call
was eliminated — saves one API call.

## Verification

- BUILD SUCCESSFUL, all 11 checklist items pass
- DEX verified: "Media" string (from @SerialName), "streamingEpisodes" in GraphQL query
- versionId=11 STABLE
- MD5: `1b3b24d3a8ab6929724dea13bac5d768`

## What should happen on device with v16.22

1. `fetchAniListId(malId)` → POST to graphql.anilist.co via WebView → parses `"Media"` correctly → returns AniList ID
2. `fetchAnikageEpisodes(anilistId)` → GET anikage.cc via WebView → thumbnails + descriptions + titles
3. AniList streamingEpisodes (cached from step 1) → additional thumbnail source
4. `fetchKitsuEpisodes(malId)` → GET kitsu.app via WebView → fallback data
5. Merge: Anikage → AniList → Kitsu → banner → anime cover
6. Episodes enriched with: accurate thumbnails, descriptions, "EP N - title" names
