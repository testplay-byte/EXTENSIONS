# Session 60 — Stream-resolution + metadata hardening (v16.13 TEST BUILD)

> Date: 2026-09-17 · Trigger: user bug report + improvement requests
> Build: v16.13 (extVersionCode 13), TEST BUILD ONLY (publish=false) — NO release, NO dist-repo changes
> Sandbox: fresh restore (both repos re-cloned with user-supplied PATs; workflow docs restored
> from repo mirror; tree-sitter+pycryptodome reinstalled; git identity "Z User <z@container>").

## 1. User's asks

1. **Bug**: specific series, LATEST episode shows no resolved video streams —
   `the-exiled-heavy-knight-knows-how-to-game-the-system` ep-12 (user browses anikoto.cz mirror).
2. **Improve metadata fetching**: "some of them do not load their metadata".
3. **AniList offline handling**: "sometimes it is offline so we should be able to handle that properly".
4. **Improve other areas** generally. TEST BUILD ONLY — no release build.

## 2. Diagnosis (all live-verified, paced probes, artifacts in /home/z/recon/)

- Server lineup CHANGED: `Vidstream-2`, `Vidstream-1beta` (new), `HD-2` — all resolve to the
  SAME megaplay iframe/data-id (179714 sub on ep-12). 3 servers = 1 file.
- megaplay `newclient.min.js?v=4.17` AES constants UNCHANGED → MegaPlayDecrypt still fine.
- `&s=bcdn` → ncdn.imgnex.top: master+variants+segments ALL 200 via plain OkHttp-like TLS.
- `&s=tcdn` / no-s → fetch.nexabloom.top: MASTER 403 to non-browser TLS (variants on that
  host are fine; master-only blocking, session-54 pattern).
- **Root cause**: v16.12's candidate loop accepted a candidate at the MASTER stage (possibly
  via the fragile shared-WebView fallback) and died if variants then failed — never trying
  bcdn. Full post-mortem: `MEMORY/issues-resolutions/05-no-streams-fresh-episodes-candidate-loop-trap.md`.

## 3. Fixes shipped (commit 1b46982, 5 files, +296/−155)

**Streams** (`AnikotoExtractors.kt`, `Anikoto.kt`, `AnikotoSettings.kt`):
- Candidate loop verifies master AND ≥1 variant before accepting; falls through otherwise.
- Candidate order: own s → bcdn (pure-OkHttp first) → discovered → tcdn → default-last.
- New `loadVariantPlaylists()` with per-variant WebView fallback.
- Dedup of identical resolved streams (label wins by preferred-server match).
- Server picker refreshed (Vidstream-1beta, HD-2 added; legacy names kept).

**Metadata** (`EpisodeMetadataFetcher.kt`, `Anikoto.kt`):
- No more poisoned cache (failures NOT cached as empty — next refresh retries).
- Parallel independent sources (Jikan ∥ AniList-id ∥ Kitsu); Anikage after AniList id.
- Dedicated short-timeout client (connect 8s / read 12s / call 20s).
- AniList: OkHttp POST first (CloudflareInterceptor handles 403), WebView fallback,
  cached-id degradation during outages (Anikage stays alive).
- Jikan 429 retry (1.2s, once); Kitsu pagination 8s wall-clock deadline.
- 25s withTimeoutOrNull ceiling on the whole enrichment phase in Anikoto.kt.

## 4. Process

- Golden rules held: NO Android SDK; tree-sitter parse OK on all 4 touched .kt before push;
  one version bump (code 12→13); NO tag, NO GitHub Release, NO dist-repo touch.
- CI: release.yml dispatch (tag v16.13, publish=false) → run 35263287060; artifact
  test-build-apks-v16.13 delivered to the user for sideload testing.
- Note for release numbering: v16.13 was ALSO the session-57 test-build number (reuse is
  fine — test numbers not reserved; devices on the OLD 16.13 test build sideload the new one).

## 5. Outstanding / next session

- User to test the v16.13 artifact: ep-12 streams, metadata loading, general playback.
- If ok → release as v16.14 (bump, do NOT reuse 16.13 — a public-ish test artifact exists
  under that name; also avoids confusion with the old s57 test build).
- Distribution APK/index untouched this session.
