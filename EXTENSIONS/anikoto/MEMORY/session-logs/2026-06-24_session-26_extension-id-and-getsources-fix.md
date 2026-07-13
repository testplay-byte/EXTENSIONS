# Session 26 — Extension ID Stability + getSources Migration (Vidstream-2 / VidCloud-1 fix)

> Date: 2026-06-24 · Session #: 26 · Duration: ~long · Timezone: America/Los_Angeles

## Goal

Three improvements requested by the user:
1. **Thorough analysis** of the Aniyomi anime extension system (so we have all needed info).
2. **Issue A**: Add a stable extension ID so updates link saved anime to the new extension.
3. **Issue B**: Fix VidCloud-1 server (doesn't work). Test with Klutzy Class Monitor EP12.
4. **Issue C**: Fix Vidstream-2 server (videos don't play). Test with Smoking Behind the Supermarket EP5.

User explicitly requested: no sub-agents, work one by one, verify along the way, follow the rules.

## What was done

### A. Thorough analysis of the Aniyomi extension system

Read the full codebase + memory + reference app source:
- `Anikoto.kt` (625 lines — main source), `AnikotoExtractors.kt`, `Models.kt`, `LocalProxyServer.kt`, `AnikotoDto.kt`
- `AnimeSource.kt` + `AnimeHttpSource.kt` stubs (ext-lib 16 API)
- `REFERENCE_HUB/aniyomi-app/.../AnimeHttpSource.kt` — the real `id`/`versionId`/`generateId` implementation
- `REFERENCE_HUB/aniyomi-app/.../AnimeExtensionManager.kt` — how the app links saved anime to extensions (`getExtensionPackage(sourceId)`)
- `REFERENCE_HUB/aniyomi-app/.../AnimeExtensionLoader.kt` — which manifest meta-data the app reads
- `MEMORY/sites/anikoto/` (all 9 docs), `MEMORY/guides/04-build-checklist.md`

Key findings documented in `MEMORY/sites/anikoto/getsources-migration-and-id-analysis.md`.

### B. Issue A: Extension ID stability (root cause + fix)

**Root cause:** The source `id = MD5("name.lowercase()/lang/versionId")` (verified in
`AnimeHttpSource.kt:58` + `AnimeExtensionManager.getExtensionPackage`). The `versionId` doc
says it should only change "if the site completely changes and urls are incompatible."
But our build checklist (item 3) REQUIRED `versionId` to match `versionCode` and be bumped
every rebuild. We bumped versionId 2→3→...→11 across sessions 16-25. Each bump changed the
source id → the app saw each build as a NEW source → saved anime were orphaned.

**Fix (v16.12):**
- Decoupled `versionId` from `versionCode` in `build.gradle.kts`:
  - `extVersionCode = 12` (bumped per build — the APK update signal)
  - `extVersionId = 11` (STABLE — do NOT bump; only change if site URL structure breaks)
- `override val versionId = 11` in Anikoto.kt (unchanged, now documented as STABLE)
- `EXTENSION_VERSION` in AnikotoLog.kt updated to "v16.12 (ext-lib 16, versionId=11 STABLE)"
- Fixed build checklist item 3: "versionCode bumped (per-build) + versionId STABLE"
- Verified: source id = MD5("anikoto/en/11") = 2869321798469315784 (stable for all future builds)
- Verified manifest versionId=11 (the app doesn't actually read this meta-data, but kept for completeness)

### C. Issue B+C: getSources migration (Vidstream-2 + VidCloud-1 fix)

**Verified by live testing** (Python + agent-browser, per rule §1 "verify before trusting"):
The old research (session 12) said VidCloud-1 was "broken" based on ONE episode. The user
said it works on other episodes. I tested the actual URLs and found the site has migrated
the player API:

| Player host | Servers | `getSourcesNew` | `getSources` | data-id per audio? |
|---|---|---|---|---|
| `vidtube.site` | VidPlay-1 | ✅ works | ✅ works (but always returns SUB — wrong for hsub/dub) | ❌ SHARED |
| `megaplay.buzz` | HD-1, Vidstream-2 | ❌ 404 | ✅ works | ✅ DIFFERENT |
| `vidwish.live` | VidCloud-1 | ❌ error page | ✅ works | ✅ DIFFERENT |

Key detail: vidtube.site's data-id is SHARED across sub/hsub/dub (needs `type` param);
megaplay.buzz/vidwish.live have audio-specific data-ids (no `type` param needed).

**The megaplay.buzz/api documentation** (user pointed here) confirmed: embed URL is
`/stream/s-2/{aniwatch-ep-id}/{language}`, "Direct Access to Embed Links are Disabled"
(Referer from an allowed domain required). Also supports MAL/AniList embeds (not needed
for our extension — we get the embed URL from anikototv.to's `/ajax/server?get=`).

**Fix (v16.13):**
1. `AnikotoExtractors.resolveVidTube`: endpoint selection by host:
   - `vidtube.site` → `getSourcesNew?id=<data-id>&type=<audio>` (unchanged — type required)
   - `megaplay.buzz`, `vidwish.live` → `getSources?id=<data-id>` (new — getSourcesNew broken)
2. `Anikoto.resolveStreamForTask`: added `vidwish.live` to the dispatch (route to
   `resolveVidTube`). Previously vidwish.live fell to the `else` branch and was skipped.

### D. Live verification (all servers × all audio types)

Tested on Wistoria S2 EP5 (has all servers × all audio types):

| Server | Host | Endpoint | sub | hsub | dub |
|--------|------|----------|-----|------|-----|
| VidPlay-1 | vidtube.site | getSourcesNew?type= | ✅ 1a1e84e3 | ✅ c2b9eb43 | ✅ ec7821b9 |
| HD-1 | megaplay.buzz | getSources | ✅ 31fcc9a2 | ✅ e452e498 | ✅ e833e7bf |
| Vidstream-2 | megaplay.buzz | getSources | ✅ 31fcc9a2 | ✅ e452e498 | ✅ e833e7bf |
| VidCloud-1 | vidwish.live | getSources | ✅ 31fcc9a2 | (n/a) | ✅ e833e7bf |

Each audio type gets a different m3u8 hash (correct audio). HD-1≡Vidstream-2 (same hashes).
VidCloud-1 same hashes, different CDN (fxpy7.watching.onl vs cdn.mewstream.buzz).

Also tested the user's specific URLs:
- **EP1 (Klutzy Class Monitor EP12)**: Vidstream-2 ✅ + VidCloud-1 ✅ (both resolve)
- **EP2 (Smoking Behind Supermarket EP5)**: Vidstream-2 ✅ (was broken!), VidCloud-1 fails
  gracefully (vidwish.live returns error page for this episode — server-side issue, not our bug)

### E. CDN note (new CDNs)

The migration introduced new CDN hosts:
- megaplay.buzz → `cdn.mewstream.buzz` (was `9hjkrt.nekostream.site`)
- vidwish.live → `fxpy7.watching.onl` (was broken before)
- vidtube.site → `mt.nekostream.site` (unchanged)

The sandbox IP is Cloudflare-blocked from `cdn.mewstream.buzz` ("Sorry, you have been
blocked"). This is an IP/ASN block on the sandbox's datacenter IP. The user's Android device
(residential/mobile IP) is expected to work, same as the old nekostream.site CDN did. Cannot
fully verify segment fetch from sandbox. The LocalProxyServer's `stripPngHeader` is defensive
(no-op if no PNG header), so it handles both PNG-wrapped and unwrapped segments.

## Files created

- `MEMORY/sites/anikoto/getsources-migration-and-id-analysis.md` — ★ the full analysis
- `MEMORY/session-logs/2026-06-24_session-26_extension-id-and-getsources-fix.md` (this log)
- `WORKSPACE/DEV/ANIKOTO/analysis/test_live_chain.py` — reusable live chain test script

## Files changed

- `src/en/anikoto/build.gradle.kts` — versionCode 11→13, versionId STABLE at 11 (documented)
- `src/en/anikoto/src/main/kotlin/.../AnikotoLog.kt` — EXTENSION_VERSION updated
- `src/en/anikoto/src/main/kotlin/.../video/AnikotoExtractors.kt` — getSources endpoint selection by host
- `src/en/anikoto/src/main/kotlin/.../Anikoto.kt` — added vidwish.live to dispatch
- `MEMORY/guides/04-build-checklist.md` — fixed item 3 (versionId STABLE, versionCode bumped)

## Builds

- **v16.12** (Issue A only): BUILD SUCCESSFUL. versionCode=12, versionId=11 STABLE.
  MD5: 1dbd1ce4f8be904ed33e836347650735. All 11 checklist items ✓.
- **v16.13** (Issue B+C): BUILD SUCCESSFUL. versionCode=13, versionId=11 STABLE.
  MD5: 5a4624aed43288de6f7959cda331b045. All 11 checklist items ✓.
  DEX verified: both `getSources` + `getSourcesNew` strings present; `vidwish.live` dispatch present.

## Status at end of session

- ✅ Thorough analysis complete (codebase + reference app + live testing)
- ✅ Issue A: versionId is STABLE at 11 — saved anime will link correctly across all future updates
- ✅ Issue B (VidCloud-1): now dispatched + uses getSources — resolves where vidwish.live has data
- ✅ Issue C (Vidstream-2): uses getSources instead of broken getSourcesNew — resolves
- ✅ All servers × all audio types verified live (Wistoria EP5: 11 of 11 combos resolve)
- ✅ User's specific test URLs verified (EP1: both resolve; EP2: Vidstream-2 resolves, VidCloud-1 fails gracefully)
- ⏳ User to test v16.13 on device (especially the new CDN cdn.mewstream.buzz — may need Cloudflare pass on the user's IP)

## Honest notes

- **The getSources migration was the real discovery.** The old research (session 12) documented
  getSourcesNew as the endpoint and marked VidCloud-1 as "broken." Both were based on testing
  before the site migrated. The site has since changed getSourcesNew→getSources on megaplay.buzz
  and vidwish.live (but NOT vidtube.site). This is why Vidstream-2 and VidCloud-1 stopped working.
  Per rule §1 (verify before trusting), I live-tested the actual URLs rather than trusting the old docs.

- **VidCloud-1 on EP2 is a server-side issue, not our bug.** vidwish.live returns an "Error - Vidcloud"
  page for that specific episode. Our extension now handles VidCloud-1 correctly where vidwish.live
  HAS the data (EP1, Wistoria). Where it doesn't, the extension logs the error and skips gracefully.

- **The sandbox can't fully test segment playback.** Cloudflare blocks the sandbox IP from
  cdn.mewstream.buzz. The API layer (getSources) works fine from the sandbox, and the m3u8 URLs
  are valid. But actual segment fetch can only be verified on the user's device. If the user's
  device is also Cloudflare-blocked (unlikely for residential/mobile), we'd need a Cloudflare
  interceptor — but the old nekostream.site CDN had the same Cloudflare layer and worked, so this
  is expected to be fine.

- **HD-1 and Vidstream-2 are still identical** (same m3u8, same data-ids). The old research noted
  this as a "dedup candidate." We didn't dedup them (the user didn't ask for that). They show as
  separate Hosters in the player UI, which is redundant but harmless.

## Next steps

User tests v16.13:
1. Install v16.13 (uninstall old extension first — same package name).
2. Test Vidstream-2 on Smoking Behind the Supermarket EP5 (should now play).
3. Test VidCloud-1 on Klutzy Class Monitor EP12 (should now play).
4. Test VidPlay-1 on any episode (should still work — unchanged).
5. Save an anime, then reinstall a future update — saved anime should link (versionId stable).
6. If audio switching or subtitles have issues, send the log from Download/1118000/.
