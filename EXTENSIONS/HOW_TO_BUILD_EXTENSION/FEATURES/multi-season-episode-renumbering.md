# Multi-Season Episode Renumbering — Implementation Guide

> **How to handle sites that continue episode numbering across seasons (e.g. Season 2 starts at
> episode 13 instead of 1).** This guide explains the renumbering method used by AnimePahe.
>
> **Location:** in the main source class's `getEpisodeList` override, after fetching all episodes
> and before building the `SEpisode` list.

---

## What this feature does

Some anime sites list each season as a separate anime entry, but continue the episode numbering from
the previous season. For example:

- **Season 1** entry: episodes 1-12
- **Season 2** entry: episodes 13-24 (NOT 1-12)

The user expects Season 2 to show episodes 1-12, not 13-24. This feature detects the offset and
renumbers the episodes so each season starts from 1.

## When to use this

Use this when the target site has this behavior. Not all sites do — some start each season from
episode 1 naturally. To check:

1. Open a multi-season anime on the site
2. Look at Season 2's episode list
3. If the first episode is > 1, the site continues numbering → use this feature

**AnimePahe** has this behavior (confirmed by the user). **AniKoto** does not (each season starts
from 1 naturally).

## The method

After fetching all episodes from the API:

```kotlin
// 1. Find the minimum episode number in the list
val minEpNum = rawEpisodes.minOfOrNull { it.episodeNumber } ?: 0f

// 2. If min > 1, the site is continuing numbering from a previous season.
//    Calculate the offset and renumber starting from 1.
val epOffset = if (minEpNum > 1f) {
    val offset = minEpNum - 1f
    Log.i(TAG, "multi-season detected (first ep=$minEpNum), renumbering with offset=$offset")
    offset
} else {
    0f  // no renumbering needed
}

// 3. Build SEpisode list with adjusted numbers
val episodes = rawEpisodes.map { (dto, epSession) ->
    val adjustedNum = dto.episodeNumber - epOffset
    SEpisode.create().apply {
        episode_number = adjustedNum
        name = "Episode ${adjustedNum.toInt()}"  // or format with floor/ceil for fractional
        setUrlWithoutDomain("/watch/$session/ep-$adjustedNum#$epSession")
    }
}
```

### How it works

| Scenario | Min ep num | Offset | Result |
|---|---|---|---|
| Normal (starts at 1) | 1.0 | 0 | No change |
| Season 2 (starts at 13) | 13.0 | 12 | 13→1, 14→2, ..., 24→12 |
| OVA (starts at 0) | 0.0 | 0 | No change (0 is intentional) |
| Mid-season start (starts at 5) | 5.0 | 4 | 5→1, 6→2, ... |

### Edge cases

- **Episode 0 (prologue/special):** min = 0, offset = 0. No renumbering. Episode 0 stays as 0. ✓
- **Fractional episodes (e.g. 12.5):** the offset is subtracted from the float. 12.5 - 12 = 0.5. ✓
- **Single episode (movie):** min = 1, offset = 0. No renumbering. ✓
- **Gaps in numbering (e.g. 1, 2, 3, 13, 14):** min = 1, offset = 0. No renumbering. The gap stays.
  This is correct — we only renumber when the ENTIRE list starts above 1, not when there's a gap.

## ★ Metadata lookup uses the ADJUSTED number

After renumbering, the metadata enrichment (Jikan/AniList/Anikage/Kitsu) must use the **adjusted**
episode number, NOT the original. This is correct because:

- If the MAL ID corresponds to the CURRENT season (e.g. Season 2's MAL ID), then Jikan's episode
  list for that MAL ID has episodes 1-12 (Season 2's episodes).
- The adjusted number (1-12) matches Jikan's numbering. ✓

If the MAL ID is WRONG (e.g. Season 1's MAL ID for a Season 2 entry), the metadata would be off.
But that's a MAL-ID-extraction issue, not a renumbering issue.

```kotlin
// In enrichEpisodesWithMetadata:
for (ep in episodes) {
    val epNum = ep.episode_number.toInt()  // ★ this is the ADJUSTED number
    val epMeta = metadata[epNum] ?: continue
    // ... apply thumbnail, title, description ...
}
```

## ★ The episode URL should use the adjusted number

The fork-compat encoding (`/watch/<session>/ep-<N>#<episodeSession>`) should use the adjusted
number for `<N>`. The `<episodeSession>` in the fragment must stay the ORIGINAL (from the API) —
it's used for video extraction.

```kotlin
setUrlWithoutDomain("/watch/$session/ep-$adjustedNum#$epSession")
//                                    ↑ adjusted      ↑ original (from API)
```

## Reference implementation

- **AnimePahe**: `EXTENSIONS/animepahe/DEV/src/.../AnimePahe.kt` §getEpisodeList (steps 5-6)
- Look for `epOffset` and `adjustedNum` in the code.

## Common issues

### Issue: renumbering triggers when it shouldn't
- **Symptom:** a single-season anime with episodes starting at 2 (missing episode 1) gets renumbered.
- **Cause:** the method assumes min > 1 = multi-season, but a missing episode 1 also triggers it.
- **Mitigation:** this is rare. Most sites either have all episodes or start from a season boundary.
  If it becomes a problem, add a threshold (e.g. only renumber if min >= 3, since a 1-2 episode gap
  is more likely a missing episode than a season boundary).

### Issue: metadata doesn't match after renumbering
- **Symptom:** episode 1 (renumbered from 13) shows Season 1 Episode 1's thumbnail/title.
- **Cause:** the MAL ID corresponds to Season 1, not Season 2. The adjusted number (1) looks up
  Season 1 Episode 1's metadata.
- **Fix:** ensure the MAL ID extraction gets the CORRECT season's MAL ID. Check the detail page's
  external links — if the site links to Season 2's MAL page, the ID will be correct.
