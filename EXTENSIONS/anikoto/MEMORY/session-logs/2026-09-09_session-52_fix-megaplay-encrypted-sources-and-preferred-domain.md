# Session 52 — Fix Megaplay Encrypted Sources (playback broken) + 6-Domain Preferred Domain Setting

> Date: 2026-09-09 · Session #: 52 · Timezone: America/Los_Angeles
> Type: CRITICAL BUGFIX + FEATURE
> Follows: session 51 (filter fixes, performance optimizations, smart search, v16.9)
> Release: v16.10 (versionCode 10, versionId 11 — unchanged)

## Goal

Two user-reported items:
1. **Video playback completely broken** — "When I try to open up any episode to play it, it fails."
   User provided a logcat capture (host app `xyz.jmir.tachiyomi.mi.debug`, episode Sakamoto Days EP10).
2. **New setting**: "Preferred domain" — the user wants to choose between all 6 site domains.

## Diagnosis (verified live from the sandbox — curl + openssl + JS analysis)

### What the user's log showed

- PATH A found 4 servers (Vidstream-2, HD-1 × sub/dub) — Kiwi disabled → correct.
- `resolveVidTube` reached `[2/5] GET getSources` and then failed on ALL of them:
  `resolveVidTube: no valid m3u8 in getSources response (sources.file='null')`
  → `getHosterList: all streams failed — returning empty`.
- The data-id extraction was CORRECT (2234 = the real id in the iframe HTML).

### Root cause — megaplay.buzz encrypted its getSources response

Live verification (same request the extension makes):

```
GET https://megaplay.buzz/stream/getSources?id=2234&type=sub
→ {"tracks":[...], "t":1, "intro":{...}, "outro":{...}, "server":4,
   "enc":"wdeBruh3qqn_i5wUNnyaPQQl1wp7r0SrL6KQPNFd24_..."}
```

- The `sources.file` field is GONE — replaced by an `enc` blob.
- The extension's DTO (`VidTubeSources.file = ""` default) parses fine but finds no URL
  → logs `sources.file='null'` → returns null → zero playable servers.
- Note: VidPlay-1 (vidtube.site) getSources still returns plaintext — episodes that only
  offer megaplay servers (like the user's) got nothing.

### How the decryption was found

1. Megaplay's player page loads `lib/newclient.min.js` (26KB, readable — the other libs are
   tamper-protected obfuscation).
2. `newclient.min.js` contains a `SegmentDecrypt` module: AES-256-CBC, key
   `"i?LMTAx0Q6,:}50U"` **zero-padded to 32 bytes** (the JS allocates `Uint8Array(32)` and
   copies the 16-char key into it — `crypto.subtle` then picks AES-256), IV
   `"W0;27ToaUpl_P%'c"`, base64url-encoded payloads. The same constants appear in its
   trust module (`trustAesKey` / `trustAesIv` defaults).
3. Decrypting a live `enc` blob with `openssl enc -d -aes-256-cbc` (same key/iv) produced:
   `{"file":"https://cdn.imgnex.top/anime/<hash>/<hash>/master.m3u8"}` ✓ identical scheme.

### The new video architecture (all verified live)

| Finding | Detail |
|---|---|
| `getSourcesNew` is BACK | Plaintext `sources.file` on ALL hosts (megaplay + vidtube + vidwish). Megaplay's own `newclient.min.js` has a `GetSourcesRewrite` module rewriting getSources → getSourcesNew — same trick we now use. |
| Rotating mirror hosts | megaplay answers with `megap.shiora.site` (with `&type=`) or `megap.mikora.top` (without); vidtube answers with `s1.akirax.buzz`. Deterministic per request shape. |
| Mirrors are WAF-free | Plain curl 200 — no WebView needed for m3u8 anymore (cdn.imgnex.top IS WAF-blocked for datacenter IPs — avoid; mirrors are the route). |
| Single quality on megaplay | Master lists ONE 1080p variant (`index-f1-v1-a1.m3u8`; f2/f3 = 404). VidPlay-1 still has 1080/720/360. |
| Segments | ALL on `p16/p19-ad-site-sign-sg.tiktokcdn.com` (content disguised as "ad" URLs), PNG-wrapped with a **252-byte prefix** (was 70). Signed URLs — Referer-agnostic. |
| PNG strip | Existing `stripPngHeader` (IEND scan → TS-sync scan, 400-byte window) handles 252 unchanged: IEND ends at 70, TS starts at 252 ✓ (verified on real segments). |
| Reference (yuzono) | Their anikototheme extractor does NOT handle `enc` either (duplicated-params trick + `SourcesSerializer` that throws) — they would fail on megaplay the same way. Our fix is ahead. |

## What was done

### 1. Video extraction fix (the critical one)

- **NEW FILE** `video/MegaPlayDecrypt.kt` — AES-256-CBC decryptor for the `enc` blob
  (base64url decode with padding fix → javax.crypto AES/CBC/PKCS5Padding, 32-byte
  zero-padded key, 16-byte IV). Logs failures via AnikotoLog.
- `AnikotoExtractors.kt`:
  - NEW `fetchSourcesData(host, dataId, audioType)`: attempt 1 = `getSourcesNew?id=X&type=Y`
    (plaintext, all hosts); attempt 2 = `getSources?id=X&type=Y` (fallback — plaintext on
    vidtube/vidwish, enc blob on megaplay → decrypt).
  - NEW `parseSourcesBody(body)`: handles both shapes — plaintext `sources.file` OR `enc`
    blob → `MegaPlayDecrypt.decrypt` → JSON `file` extraction.
  - NEW `SourcesData` (master m3u8 + tracks) — subtitle tracks flow through both endpoints.
  - `vidtubeApiHeaders(host)` — per-host Referer (was hardcoded vidtube.site).
  - Both attempts re-throw `CancellationException` properly (session-51 rule).
- `AnikotoDto.kt`: `VidTubeSourcesResponse` gained `enc: String? = null`;
  `VidTubeSources.file` is now `String?` (tolerates null).

### 2. Preferred domain setting (6 domains)

- The 6 domains were verified from **anikoto.site** (the site's own "Official domain hub")
  + live probing: `anikototv.to` (primary, 200), `anikoto.cz` (200), `anikoto.me` (200),
  `anikoto.net` (200), `anikototv.se` (200), `anikototv.com` (200, redirects to its own
  /home/ — live mirror, not on the hub page).
- `AnikotoSettings.kt`: new `PREF_DOMAIN_KEY = "pref_domain"` (default `https://anikototv.to`)
  + `preferredDomain` getter + **ListPreference** at the top of the Playback category
  ("Preferred domain", 6 entries, "Currently: %s" per repo convention).
- `Anikoto.kt`: `override val baseUrl` is now `by lazy { prefs[PREF_DOMAIN_KEY] ?: DEFAULT_BASE_URL }`.
  - **Source ID is unaffected** — id = MD5("anikoto 180/en/11") derives from name/lang/versionId,
    NOT the domain → switching mirrors never orphans saved anime.
  - Episode URLs are stored as relative paths (`/watch/slug/ep-N#fragment`) by design
    (session 43 fork-compat work) → saved episodes resolve against the new domain automatically.
  - `DEFAULT_BASE_URL` added to the companion object.

### 3. Version bump

- `build.gradle.kts`: `extVersionCode = 10` → versionName `16.10` (starts with "16." ✓).
- `AnikotoLog.kt`: `EXTENSION_VERSION = "v16.10 (ext-lib 16, versionId=11 STABLE)"`.
- `src/lib/site-config.ts`: AniKoto card → v16.10, build 10, date September 9, 2026.
- versionId stays **11** (STABLE — never bump).

## Verification

### Live chain verification (sandbox, before building)

| Step | Result |
|---|---|
| iframe page → data-id | ✅ `data-id="2234"` (matches user's log) |
| getSources | ❌ enc blob (root cause confirmed) |
| getSourcesNew | ✅ plaintext → `megap.shiora.site/.../master.m3u8` |
| enc AES decrypt (openssl) | ✅ `{"file":"https://cdn.imgnex.top/..."}` |
| mirror master.m3u8 | ✅ 200 (WAF-free), 1× 1080p variant |
| variant playlist | ✅ 200, 320 segments = 23.8 min full episode |
| segment (first + mid) | ✅ 200, PNG prefix → TS at offset 252, strip algorithm verified |
| segment headers | ✅ Referer-agnostic (200 with none) |
| VidPlay-1 chain | ✅ vidtube getSourcesNew → `s1.akirax.buzz` master (1080/720/360) |

### Build

CI (GitHub Actions `Build (CI)` on push to main): ✅ BUILD SUCCESSFUL — all 7 extensions compile.
Release (tag v16.10): signed release APK `aniyomi-en.anikoto180-v16.10-release.apk` published.
(No local Android SDK — builds happen in CI per project setup.)

## Key decisions

1. **getSourcesNew first, getSources as encrypted-fallback** — the client's own rewrite proves
   getSourcesNew is the intended path now; the fallback keeps vidtube/vidwish working even if
   getSourcesNew disappears again (it did flip-flop before: session 26 migration, session 52 back).
2. **Decrypt fallback implemented anyway** — if the site re-encrypts getSourcesNew too, the
   extension still works via getSources + AES. Belt and suspenders.
3. **No WebView for the new mirrors** — they are WAF-free; forcing WebView would just add
   latency. `isWafBlockedHost()` left unchanged (mewstream/voltara/zaptrix).
4. **Domain setting placed in Playback category** (first item) — the user asked for it "in the
   settings"; Playback is where the other site-affecting dropdowns live. Domain switching is
   instant (lazy read per source instance; takes effect after app restart or source reload).
5. **versionId untouched** — domain setting must not orphan saved anime.

## Files changed

| File | Change |
|---|---|
| `video/MegaPlayDecrypt.kt` | NEW — AES-256-CBC decryptor for megaplay `enc` blobs |
| `video/AnikotoExtractors.kt` | getSourcesNew-first strategy + dual-shape parser + per-host API Referer |
| `AnikotoDto.kt` | `enc` field added, `file` nullable |
| `AnikotoSettings.kt` | Preferred domain pref (key, getter, 6-entry dropdown in Playback) |
| `Anikoto.kt` | `baseUrl` now user-selectable (lazy from prefs) + `DEFAULT_BASE_URL` |
| `build.gradle.kts` | extVersionCode 9 → 10 |
| `AnikotoLog.kt` | EXTENSION_VERSION → v16.10 |
| `src/lib/site-config.ts` | Download page card → v16.10 / build 10 |
| `MEMORY/sites/getsources-migration-and-id-analysis.md` | §4: session-52 findings (enc, key/IV, mirrors, segments) |
| `MEMORY/EXTENSIONS.md`, `EXTENSION.md`, `APK_INFO.md`, `README.md`, `modules/03`, `modules/05` | Registry/quick-ref/doc updates |

## Next steps (for the next session)

- User tests v16.10 on-device: playback on megaplay servers (HD-1, Vidstream-2), VidPlay-1,
  and the new Preferred domain dropdown.
- If megaplay rotates the `enc` key/IV, they live in `newclient.min.js` — re-extract and
  update `MegaPlayDecrypt` (the constants are also in the trust module defaults).
- The mirror hosts (shiora/mikora/akirax) could start requiring headers/WAF — if m3u8 fetches
  403, add them to `isWafBlockedHost()` in both `AnikotoExtractors` and `LocalProxyServer`.

## Status

- ✅ Root cause identified and verified live (encrypted getSources on megaplay)
- ✅ Fix implemented (getSourcesNew + AES decrypt fallback) — compiles in CI, release published
- ✅ Preferred domain setting (6 verified domains) implemented
- ✅ Documentation updated (session log, sites doc, registry, quick-ref, module docs)
- ⏳ On-device playback verification pending (user tests — per project rule §9: the user tests)
