# Session 03 — MKissa: Fix Episode Order + Update Build Guide

> Date: 2027-06-29 · Extension: MKissa 180 (mkissa.to) · Session: 03 · Status: ✅ episode order fixed + guide updated

## Goal

Fix the two issues the user reported after testing v16.2:
1. Episode order reversed (showing 13→1 instead of 1→13)
2. Popular/Latest mixed up (user reported, but investigation showed code is correct)

Then update the HOW_TO_BUILD_EXTENSION guide with lessons learned from MKissa to make future extension creation smoother.

## What was done

### Issue 1: Episode order (FIXED)

**Investigation:**
- Checked what order the allanime reference returns episodes: the API returns `["12", "11", ..., "1", "0"]` (descending), and the reference maps directly WITHOUT reversing → returns DESCENDING.
- Checked what animepahe returns: builds episodes with `sort=episode_asc` (ascending from API), then returns `.reversed()` → DESCENDING.
- Both reference extensions return DESCENDING order (latest episode first).
- My MKissa code returned ASCENDING (`.sorted()`) — the OPPOSITE of both references.

**Root cause:** Aniyomi displays episodes in REVERSE of the order returned by the extension (so the latest episode appears at the top by default). Returning ascending → Aniyomi reverses → user sees descending (13→1). Returning descending → Aniyomi reverses → user sees ascending (1→13).

**Fix:** Changed `.sorted()` to `.sortedDescending()` in `buildEpisodeList`. Now returns DESCENDING (ep 12 first, ep 0 last). Aniyomi reverses to display ASCENDING (ep 1 first). Matches the allanime reference + animepahe convention.

### Issue 2: Popular/Latest (investigated — code is correct)

**Investigation:**
- Re-tested both API calls via curl with the exact variables the Kotlin code sends:
  - Popular (`queryPopular`, dateRange=7): returns Tongari Boushi, Wistoria S2, Slime S4, Classroom of the Elite — well-known popular anime ✅
  - Latest (`shows`, sortBy=Recent): returns Fanren Xiu Xian Zhuan, 1P, Onegai AiPri, Kuroneko, Doupo — recently-updated anime ✅
- The two lists are clearly DIFFERENT — they're NOT swapped.
- Checked animepahe for comparison: animepahe uses the SAME endpoint (`/api?m=airing`) for BOTH Popular AND Latest — so on animepahe, both tabs show the same content. On MKissa they show different content (which is the correct behavior for a site that has distinct popular and latest APIs).

**Conclusion:** The MKissa popular/latest code is correct. The Popular tab returns popular anime (ranked by views), and the Latest tab returns recently-updated anime. They're different lists, verified via API testing. The user may have been comparing to animepahe's behavior (where both tabs show the same content) or may have been confused by the different anime appearing in each tab.

**No code change needed** for this issue — the implementations are correct.

### Build v16.3
- Bumped versionCode from 2 to 3.
- Built debug APK: `aniyomi-en.mkissa180-v16.3-debug.apk` (186KB).
- Build checklist ALL PASS: package=...en.mkissa180 v16.3, Stub! count=0, extClass=FULL path.
- Copied to APK/ (removed old v16.2).

### Guide updates (the user's second request)

Updated the HOW_TO_BUILD_EXTENSION guide with lessons learned from MKissa:

1. **`common-pitfalls.md`** — added 3 new pitfall sections:
   - **GraphQL API Sites**: querying subfields on scalar Object types causes GRAPHQL_VALIDATION_FAILED (the details query bug from session 02)
   - **GraphQL API Sites**: using GET+APQ instead of POST+full-query (fragile hashes)
   - **Episode Display Order**: returning ascending causes reversed display (the episode order bug from this session)
   - **Response Parsing**: double-`use` on OkHttp Response (the bug from session 02)

2. **`reference-prior-solutions.md`** (renamed from `reference-anikoto-solutions.md`) — added a new "GraphQL API Sites (MKissa)" section with 5 solutions:
   - GraphQL details query returns all-null fields (scalar Object fields)
   - Episode order reversed (Aniyomi reverses the extension's list)
   - Episode metadata enrichment without MAL external links (AniList ID extraction)
   - Double-`use` on OkHttp Response
   - Site uses a GraphQL API (SPA → check network requests)
   - Updated the header + format to cover all extensions (not just AniKoto)

3. **`03-details-and-episodes.md`** — fixed the episode order convention:
   - The old guide said `.reversed()  // Aniyomi expects ascending order (ep 1 first)` — this was WRONG/misleading.
   - Updated to: return in DESCENDING order, with a clear explanation of WHY (Aniyomi reverses for display).
   - Updated §3.6 (metadata enrichment) to document both approaches: MAL ID (AniKoto/AnimePahe pattern) + AniList ID (MKissa pattern).
   - Updated §3.7 verification checklist to include the descending-order check.

4. **`01-analyze-the-website.md`** — added a new section "If the site is a SPA — check network requests":
   - Explains how to use agent-browser to capture API calls for SPA sites.
   - References the MKissa lesson (SvelteKit frontend on api.allanime.day GraphQL API).

5. **`FEATURES/episode-metadata-enrichment.md`** — added a new section "AniList ID extraction (alternative to MAL ID — MKissa pattern)":
   - Documents the `bx(\d+)-` regex for extracting the AniList media ID from thumbnail URLs.
   - Explains the Anikage + Jikan approach (OkHttp-only, no WebView needed).
   - Includes a comparison table: when to use MAL ID vs AniList ID vs title-search.

6. **`FEATURES/README.md`** — added MKissa to the reference table (metadata enrichment ✅, video playback 🚧 in progress).

7. **`README.md`** (master guide) — updated all references from `reference-anikoto-solutions.md` to `reference-prior-solutions.md`, updated the description to cover all extensions.

## What worked
- ✅ Episode order fix: returning descending → Aniyomi displays ascending. Matches both reference extensions.
- ✅ Popular/Latest verified correct via API testing — no swap needed.
- ✅ Guide updates comprehensive: 7 files updated with MKissa lessons, covering GraphQL APIs, episode order, metadata enrichment alternatives, SPA site analysis.
- ✅ The renamed `reference-prior-solutions.md` now covers all 3 extensions (AniKoto + AnimePahe + MKissa) and is ready for future extensions.

## What's next (Step 4 — video playback)
The user confirmed they're ready to move on to video extraction. The 4 servers (Fm-Hls, Uni, Mp4, Ok) + the Cloudflare managed challenge on the watch page will be the focus of the next session.
