# ANIKOTO — Audio Types & Labeling

> Last updated: 2026-06-22 · Status: VERIFIED
> Rule §7: 3 audio types (SUB, HSUB, DUB) — not 2. Rule §8: sub/dub availability in `SEpisode.scanlator`.

## The 3 audio types

| Our label | Site label (VidPlay/HD/Vidstream) | Site label (Kiwi) | data-type attr | getSourcesNew `type` param | Has VTT subs? | Has separate m3u8? |
|---|---|---|---|---|---|---|
| `SUB` | SUB | — (Kiwi has no pure SUB) | `sub` | `sub` | ✅ English | ✅ unique hash |
| `HSUB` | HSUB | H-SUB (mapper "sub" key) | `hsub` | `hsub` | ❌ (hardsubbed) | ✅ unique hash |
| `DUB` | DUB | A-DUB (mapper "dub" key) | `dub` | `dub` | ✅ English | ✅ unique hash |

> **Labeling clarification (per user):** "the first four servers mark H-SUB as HSUB and the kiwi one marks it as H-SUB but all of these are the same. I have been using H-SUB for all. And the same goes for the kiwi servers, A-DUB it's the same as other servers DUB."
>
> Our extension uses `SUB` / `HSUB` / `DUB` consistently (3 chars max, clean).

## Per-server audio availability (test episode: Wistoria S2 EP5)

| Server | SUB | HSUB | DUB | Notes |
|---|---|---|---|---|
| VidPlay-1 | ✅ | ✅ | ✅ | All 3. Same data-id (138029) across types. |
| HD-1 | ✅ | ✅ | ✅ | All 3. Different data-ids per type (176012/176261/176502). |
| Vidstream-2 | ✅ | ✅ | ✅ | All 3. Same data-ids as HD-1 (likely same backend). |
| VidCloud-1 | ✅ | ❌ | ✅ | Only sub+dub (NO hsub). ❌ BROKEN anyway (getSourcesNew 404). |
| Kiwi-Stream | ❌ | ✅ | ✅ | Only hsub+dub (no pure SUB). mapper "sub"=HSUB, "dub"=DUB. |

## Qualities per audio type

ALL working servers × ALL audio types offer the SAME 3 qualities (verified on VidPlay-1 SUB + DUB, HD-1 SUB, Kiwi SUB):
- 1080p (1920×1080, 5.5 Mbps)
- 720p (1280×720, 2.8 Mbps)
- 360p (640×360, 800 kbps)

The master m3u8 always has exactly these 3 `#EXT-X-STREAM-INF` variants. No other resolutions observed.

## Episode-level availability (rule §8 — scanlator field)

Each episode `<a>` in `/ajax/episode/list/{animeId}` response has:
- `data-sub="1"` — SUB available (1/0)
- `data-dub="1"` — DUB available (1/0)
- (no `data-hsub` attribute — HSUB availability must be inferred from the server list)

**Strategy for `SEpisode.scanlator`** (rule §8 — shows below episode name):
1. Read `data-sub`, `data-dub` from the episode `<a>`.
2. After fetching the server list, check if any `<div data-type="hsub">` section has `<li>` items → HSUB available.
3. Build `scanlator` = `"SUB • HSUB • DUB"` (only the available ones, separated by ` • `).
4. Keep `SEpisode.name` clean: `"Episode 5"` (no audio tags in the name).

## Per-video labeling (in `Video.videoTitle`)

For each `Video` returned by `videoListParse(response, hoster)`:
- Format: `"{AUDIO} - {QUALITY}p - {SERVER_NAME}"`
- Examples: `"SUB - 1080p - VidPlay-1"`, `"HSUB - 720p - HD-1"`, `"DUB - 1080p - Kiwi-Stream"`
- The `AUDIO` and `SERVER_NAME` come from the hoster's context (which audio type + which server). The `QUALITY` comes from the m3u8 variant.
- Set `Video.preferred = true` on exactly ONE video per hoster (the user's preferred audio + quality + server, from prefs).

## Kiwi-Stream audio mapping (CRITICAL)

The `mapper.nekostream.site` API returns:
```json
{"Kiwi-Stream-": {"sub": {"url": "..."}, "dub": {"url": "..."}}}
```
But per `mapper.js`'s `serverStructure`:
- `serverStructure.sub` template renders the `H-SUB` label (hardsub)
- `serverStructure.dub` template renders the `A-DUB` label (dub)

So Kiwi's mapper "sub" key = **HSUB** (not SUB!), and "dub" key = **DUB**. This is the user's clarification: "the kiwi one marks it as H-SUB... A-DUB it's the same as other servers DUB."

**In our extension:** when processing Kiwi-Stream, treat the mapper's `sub` as `HSUB` and `dub` as `DUB`. Label videos accordingly.
