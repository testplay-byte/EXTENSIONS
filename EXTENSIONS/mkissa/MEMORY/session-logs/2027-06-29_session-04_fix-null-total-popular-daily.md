# Session 04 — MKissa: Fix Null Total JSON Crash + Popular Daily

> Date: 2027-06-29 · Extension: MKissa 180 (mkissa.to) · Session: 04 · Status: ✅ JSON crash fixed + popular dateRange corrected

## Goal

Fix the two issues the user reported after testing v16.3:
1. `JsonDecodingException` when scrolling the Latest section (`pageInfo.total` was `null`)
2. Popular/Latest "not configured properly" — the user shared the site's Popular URL (`range=1` = Daily) and Latest URL for verification

The user clarified: "no need to change the places of latest and popular" — the Popular/Latest assignment is correct, but the Popular dateRange needs to match the site's default (Daily, not Weekly).

## What was done

### Fix 1: JsonDecodingException on null total (CRITICAL)

**The error the user saw:**
```
JsonDecodingException: Unexpected JSON token at offset 38: Unexpected symbol 'n' in numeric literal at path: $.data.shows.pageInfo.total
JSON input: .....{"shows": {"pageInfo":{"total":null},"edges":[{"_id":"8fMjaEa.....
```

**Root cause:** The `api.allanime.day` API intermittently returns `pageInfo.total: null` (especially during cache refreshes while scrolling). My DTO had `val total: Int = 0` (non-nullable) — kotlinx.serialization can't decode `null` to a non-nullable `Int`, so it crashes.

**Investigation:** I tested extensively via curl:
- High page numbers (page 1000, 700): `total=24588` (not null) — the API wraps around past the last page
- Search with no results: `total=24588`, `edges=[]` — total is the database total, not the search count
- The `null` total is intermittent — likely happens during server-side cache refreshes

**Fix:** Made `total` nullable in both DTOs:
- `PageInfo.total`: `Int = 0` → `Int? = null`
- `QueryPopular.total`: `Int = 0` → `Int? = null`

Now when the API returns `total: null`, kotlinx.serialization decodes it as `null` instead of crashing. The `hasNext` logic uses the full-page heuristic (partial page = last page), which doesn't depend on `total` — so pagination still works correctly.

### Fix 2: Popular dateRange (Daily, not Weekly)

**The issue:** The user's Popular URL is `https://mkissa.to/popular?type=anime&range=1` — `range=1` means **Daily** popular. My code used `dateRange=7` (Weekly). The two return DIFFERENT anime:
- `dateRange=1` (Daily): first = "Tsue to Tsurugi no Wistoria Season 2"
- `dateRange=7` (Weekly): first = "Tongari Boushi no Atelier"

The user was seeing Weekly popular in the extension's Popular tab, but Daily popular on the site — the mismatch made it look "not configured properly".

**Fix:** Changed `dateRange` from `7` to `1` in `popularAnimeRequest`. Now the extension's Popular tab matches the site's default Daily popular view (`mkissa.to/popular?type=anime&range=1`).

**Note:** The user explicitly said "no need to change the places of latest and popular" — the Popular tab correctly uses `queryPopular` and the Latest tab correctly uses `shows` + `sortBy:Recent`. No swap needed; just the dateRange fix.

### Build v16.4
- Bumped versionCode from 3 to 4.
- Built debug APK: `aniyomi-en.mkissa180-v16.4-debug.apk` (186KB).
- Build checklist ALL PASS: package=...en.mkissa180 v16.4, Stub! count=0.
- Copied to APK/ (removed old v16.3).
- Webpage updated to v16.4, Build 4.

## What worked
- ✅ JSON null-total crash fixed — nullable `Int?` handles the intermittent `null` gracefully.
- ✅ Popular dateRange corrected to Daily (1) — matches the site's default popular page.
- ✅ Popular/Latest tab assignment confirmed correct (no swap needed, per user's instruction).
- ✅ hasNext logic uses the full-page heuristic — doesn't depend on `total`, so it works even when `total` is null.

## What's next (Step 4 — video playback)
The user is ready to move on to video extraction. The 4 servers (Fm-Hls, Uni, Mp4, Ok) + the Cloudflare managed challenge on the watch page will be the focus of the next session.
