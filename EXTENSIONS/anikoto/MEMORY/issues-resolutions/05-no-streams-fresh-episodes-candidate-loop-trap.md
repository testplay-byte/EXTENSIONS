# 05 — "No resolved video streams" on fresh episodes (candidate-loop trap)

> Session 60 (2026-09-17) · Status: ✅ ROOT-CAUSED + FIXED (v16.13 test build) · Live-verified end-to-end

## Symptom (user report)

For "The Exiled Heavy Knight Knows How to Game the System" the LATEST episode (ep-12,
`https://anikoto.cz/watch/the-exiled-heavy-knight-knows-how-to-game-the-system/ep-12`)
shows NO resolved video streams at all — no servers, nothing. Other content played fine
on v16.12.

## What the live chain looks like NOW (verified 2026-09-17 with paced Python probes)

1. **Server lineup changed.** Server list for this series (ep-11 AND ep-12) is now
   `Vidstream-2`, `Vidstream-1beta` (NEW name), `HD-2` — VidPlay-1 / HD-1 / VidCloud-1 are gone.
2. **All three resolve to the SAME megaplay iframe + data-id.**
   - Vidstream-2 → `https://megaplay.buzz/stream/s-2/831149/sub` (NO `s=` param)
   - Vidstream-1beta → `https://megaplay.buzz/videojs/stream/s-2/831149/sub` (NO `s=` param)
   - HD-2 → `https://megaplay.buzz/stream/s-2/831149/sub?s=bcdn`
   - All → `data-id="179714"` (sub). Three servers = one file.
3. **getSources/getSourcesNew still return the `enc` AES blob** (171 chars) and the
   decryption constants in megaplay's `lib/newclient.min.js?v=4.17` are UNCHANGED
   (`i?LMTAx0Q6,:}50U` / `W0;27ToaUpl_P%'c` — still present verbatim in the JS).
   MegaPlayDecrypt still works. NOT the bug.
4. **CDN landscape (per `s=` candidate, decrypted file host):**
   - `&s=bcdn` → `ncdn.imgnex.top/.../master.m3u8` → **master 200, variants 200,
     segments 200 via plain non-browser HTTP (python-requests ≈ OkHttp)** ← the
     OkHttp-friendly CDN
   - `&s=tcdn` and NO-s → `fetch.nexabloom.top/.../master.m3u8` → **master 403** to
     non-browser TLS (openresty) — but the VARIANT playlists on the same host ARE
     fetchable (200). Master-only blocking, same pattern as session 54.
5. Subtitle tracks in the getSources response now point at a new random-subdomain CDN
   family (`f0ja7.zhaevor.top`). Segments on the bcdn chain are on `bb.akirax.buzz`,
   raw MPEG-TS (NOT PNG-wrapped — proxy's magic-byte strip is a no-op for them, safe).

## Root cause (extension logic, not the site)

In v16.12, for an iframe WITHOUT an `s=` param (Vidstream-2 / Vidstream-1beta), the
candidate order was `[tcdn, bcdn, ""]` (page-discovered tcdn first):

1. `tcdn` → enc decrypts → master on `fetch.nexabloom.top` → OkHttp 403 →
   **WebView master fallback** (single shared WebView, `synchronized(fetchLock)`,
   30s timeout, hit by 3 parallel server tasks at once → serialized, slow, fragile).
2. If the WebView returned the master text, the candidate was ACCEPTED at the master
   stage — but if the variant stage then failed (or the WebView itself failed/timed
   out under contention), `resolveVidTube` returned null **without ever trying the
   `bcdn` candidate** that works via pure OkHttp.
3. All 3 servers share one data-id → all fail the same way → 0 hosters →
   "no resolved video streams".

I.e. a master-verified-but-variants-failed (or WebView-hiccup) candidate poisoned the
whole resolution. bcdn was structurally unreachable exactly when it was needed.

## Fix (v16.13 test build)

`AnikotoExtractors.kt`:
- **Candidate loop moved into `resolveVidTube`**: a candidate now wins only when the
  master AND ≥1 variant playlist both verify; otherwise fall through to the next
  candidate (old per-candidate helper `fetchSourcesData` removed).
- **Candidate order**: iframe's own `s=` → **`bcdn`** (pure-OkHttp path first — no
  WebView detour in the common case) → page-discovered selectors → `tcdn` → default "".
- **Per-variant WebView fallback** in new `loadVariantPlaylists()` — CDN rotation can
  spread the master-only-403 pattern to variant playlists at any time.

`Anikoto.kt`:
- **Dedup identical resolved streams** (key = audio label + first segment URL;
  preferred-server label wins). The 3 same-file server entries now collapse to 1 hoster.

`AnikotoSettings.kt`: server picker refreshed (Vidstream-1beta, HD-2 added; legacy
names kept — sorter uses `contains()`, stale entries are harmless).

## Guardrails that made diagnosis fast

- Recon script traced the whole chain with 0.9s pacing (watch page → episode list →
  server list → per-server resolve → iframe → getSources×2 → decrypt → master →
  variant → segment HEAD). Saved artifacts under `/home/z/recon/` (sandbox-local).
- openssl/pycryptodome cross-check of the enc blob against the current
  newclient.min.js constants before suspecting the decryptor.

## Lessons

- "Verify-then-accept" must cover EVERY stage that can fail, not just the first one.
- Anything routed through the shared WebView is fragile under parallel load — design
  candidate orders so the WebView is the LAST resort, not the common path.
- Server names rotate (Vidstream-1beta appeared; VidPlay-1/HD-1/VidCloud-1 vanished).
  Never hardcode a name where a behavior is meant.
