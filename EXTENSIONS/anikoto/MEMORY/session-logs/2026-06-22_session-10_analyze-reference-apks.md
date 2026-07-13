# Session 10 — Deep Analysis of Both Reference APKs + Best-Method ADR

> Date: 2026-06-22 · Session #: 10 · Duration: ~long · Timezone: America/Los_Angeles

## Goal

The user provided a second reference APK (`anikoto-refrence-v16.4.apk`) and asked to:
1. Move it to `APK/REFERENCE/`.
2. Deeply analyze BOTH reference APKs (`anikoto-by-1118000-v3.apk` + `anikoto-refrence-v16.4.apk`).
3. Understand the working, logics, and techniques so we can build extensions like these.
4. Determine the **best method** to create extensions.

Per rule §1: the reference APKs are a **cross-check / understanding source only** — never a copy source. We build from understanding.

## What was done

### A. Moved the v16.4 APK to `APK/REFERENCE/`
- `mv /home/z/my-project/upload/anikoto-refrence-v16.4.apk /home/z/my-project/APK/REFERENCE/`
- Both reference APKs now co-located in `APK/REFERENCE/`.

### B. Installed jadx 1.5.3 (decompiler)
- No decompilation tools were present. Installed jadx (the best Android DEX→Java decompiler, decodes manifest + resources, CLI mode) to `/home/z/my-project/.tools/jadx/`.
- Downloaded from `https://github.com/skylot/jadx/releases/download/v1.5.3/jadx-1.5.3.zip` (116 MB).
- Verified: `jadx --version` → `1.5.3`.

### C. Decompiled both APKs with jadx
- Output: `/home/z/my-project/.tools/apk-out/v3/` and `/home/z/my-project/.tools/apk-out/v16-4/`.
- Command: `jadx -q --no-debug-info --no-inline-anonymous --no-replace-consts -d <out> <apk>`.
- For v3's coroutine bodies that jadx dumped as "Method dump skipped", re-decompiled with `jadx --show-bad-code` and cross-checked against v16.4's cleanly-decompiled equivalents.

### D. High-level static analysis (both APKs)
Captured manifest, badging, DEX structure, package names, versions, build types via `aapt2 dump badging` + `unzip -l` + decoded manifests.

**Key findings (v3 vs v16.4):**
| | v3 | v16.4 |
|---|---|---|
| Package | `...en.anikotofinal` | `...all.anikoto` |
| versionName | 16.1 | 16.4 |
| Build | debug (non-obfuscated) | release (R8-obfuscated) |
| Size | 250 KB | 1.1 MB (single 3.3 MB DEX) |
| Author | 1118000 | salmanbappi |
| baseUrl | `anikoto.cz` (stale) | `anikototv.to` (current) |
| lang | en | all |
| Kotlin stdlib bundled | no | yes (bloat) |
| Apache Commons bundled | no | yes (722 files, bloat) |
| keiyoushi/utils bundled | no | yes (75 files, but UNUSED by Anikoto class) |
| Mapper API | used (optional) | dropped |

### E. Launched 3 parallel subagents for deep code analysis
The v3 APK is clean/non-obfuscated, making it the primary reading reference. Three subagents (Task IDs 2-a, 2-b, 2-c) deep-read different aspects in parallel, each appending to the shared worklog:

- **2-a (AnikotoFinal.java, 1346 lines)**: extracted the complete catalog + video-pipeline logic — class decl, HTTP setup (two-client split), catalog endpoints + selectors, the `getHosterList` flow (discovery → parallel resolution → VidTube extractor), `resolveVideo`, `sortVideos`, the endpoint inventory.
- **2-b (LocalProxyServer 1236 lines + AnikotoRC4 57 + MapperStreamToken 88)**: extracted the local proxy architecture (raw ServerSocket, index-based URL scheme, build-from-scratch m3u8, PNG-strip two-pass algorithm, LRU cache, prefetch), the RC4 crypto (key `"simple-hash"`, textbook RC4), the mapper token parsing.
- **2-c (DTOs + filters + extensions/utils toolkit)**: extracted the 6 `@Serializable` DTOs, the 5 filters (43 genres, 6 types, 3 statuses, 2 langs, 8 sorts), the `EpisodeMeta` pipe-encoding, the custom `extensions.utils` toolkit (~660 LOC) vs keiyoushi.utils.

### F. v16.4 comparison (done directly)
Confirmed v16.4 is a **refactored, smaller-source version (911 lines)** of the same architecture. Key differences: `baseUrl` updated to `anikototv.to`, `lang="all"`, mapper API dropped, `NetworkKt` + `UrlUtils` added to the custom utils, `usesCleartextTraffic="true"` (needed for localhost proxy in release). RC4 key `"simple-hash"` identical in both.

### G. Wrote the MEMORY documentation (5 analysis files + 1 ADR)
Created in `MEMORY/research/apk-reference/`:
1. `01-apk-overview.md` — v3 vs v16.4 at-a-glance comparison.
2. `02-video-pipeline-and-proxy.md` — ★ the Hoster flow + LocalProxyServer + RC4 + mapper (the heart).
3. `03-catalog-and-dtos.md` — catalog endpoints, filters, DTOs, EpisodeMeta encoding.
4. `04-toolkit-and-utils.md` — the custom `extensions.utils` toolkit vs keiyoushi/utils.
5. `05-cross-check-lessons.md` — what we LEARN + the best-method synthesis.

Created in `MEMORY/decisions/`:
- `03-best-method-to-build-extensions.md` — ★ the 12-point ADR for the best method.

Updated:
- `APK/REFERENCE/README.md` — added v16.4 row, marked analysis complete.
- `MEMORY/research/apk-reference/README.md` — marked analysis complete, listed the 5 files.
- `MEMORY/decisions/README.md` — added the ADR 03 entry.

## Key findings / decisions

1. **Our live-site research (`MEMORY/sites/anikoto/`) was substantially correct.** The reference APK confirms the 5 servers, 3 audio types, VidCloud-1 being broken, HD-1≡Vidstream-2, all the endpoints, the PNG wrapping, the episode-list flow, the scanlator convention. This is a strong validation of the agent-browser-based research approach.

2. **The reference CORRECTED/EXTENDED several points**: the vrf algorithm (RC4 key `"simple-hash"` — we had noted the param but not the algorithm), the PNG-strip algorithm (IEND+8 + 0x47@188 — robust, not hardcoded to 70 bytes), the `EpisodeMeta` pipe-encoding pattern (eliminates a re-fetch), the two-client split (Cloudflare vs clean), the index-based proxy URL scheme (vs URL-rewriting), the `initialized=true` Video flag, the `usesCleartextTraffic` requirement for release.

3. **The site migrated domains**: `anikoto.cz` (v3) → `anikototv.to` (v16.4). Our live research already uses `anikototv.to` — we're current. v3's baseUrl is stale.

4. **v3's lean build (250 KB, no stdlib, all deps compileOnly) is the correct approach; v16.4's bloated build (3.3 MB, stdlib + Apache + unused keiyoushi) is an anti-pattern.** Our session-08 build (80 KB) is the leanest correct approach.

5. **The best method (ADR 03)** is a 12-point synthesis: AGP 8.13.2 + Gradle 8.14.3 + Kotlin 2.2.x + Java 17 + ext-lib v16 stubs from source; minimal self-rolled `extensions.utils` (~700 LOC, NOT keiyoushi.utils); all deps `compileOnly`; two-client split; `EpisodeMeta` pipe-encoding; RC4 vrf; index-based local proxy; PNG-strip; parallel Hoster resolution; `initialized=true` videos; sort+prefer; R8 release; defensive coding.

6. **Our session-08 `Anikoto.kt` needs upgrades before Stage 4**: implement `AnikotoRC4` (vrf is server-validated, currently a runtime blocker), upgrade `SEpisode.url` to `EpisodeMeta` encoding, add the `Source` base class, split into two clients.

## Files created / changed

- `/home/z/my-project/.tools/jadx/` — jadx 1.5.3 decompiler (installed)
- `/home/z/my-project/.tools/apk-out/v3/` + `/home/z/my-project/.tools/apk-out/v16-4/` — decompilation scratch outputs (NOT in workspace)
- `APK/REFERENCE/anikoto-refrence-v16.4.apk` — moved from upload/
- `MEMORY/research/apk-reference/01-apk-overview.md` — NEW
- `MEMORY/research/apk-reference/02-video-pipeline-and-proxy.md` — NEW (★ the heart)
- `MEMORY/research/apk-reference/03-catalog-and-dtos.md` — NEW
- `MEMORY/research/apk-reference/04-toolkit-and-utils.md` — NEW
- `MEMORY/research/apk-reference/05-cross-check-lessons.md` — NEW (★ best-method synthesis)
- `MEMORY/decisions/03-best-method-to-build-extensions.md` — NEW (★ the ADR)
- `APK/REFERENCE/README.md` — updated (v16.4 row + analysis-complete status)
- `MEMORY/research/apk-reference/README.md` — updated (analysis-complete + file list)
- `MEMORY/decisions/README.md` — updated (ADR 03 entry)
- `MEMORY/session-logs/2026-06-22_session-10_analyze-reference-apks.md` — this log

## Status at end of session

- ✅ Both reference APKs moved to `APK/REFERENCE/` and deeply analyzed (jadx decompilation + 3 parallel subagent deep-reads + v16.4 comparison).
- ✅ 5 analysis files written in `MEMORY/research/apk-reference/` covering overview, video pipeline + proxy + crypto, catalog + DTOs, toolkit, and cross-check lessons.
- ✅ Best-method ADR written (`MEMORY/decisions/03-best-method-to-build-extensions.md`) — the 12-point method.
- ✅ Our live-site research validated (substantially correct) and refined (vrf algorithm, PNG-strip algorithm, EpisodeMeta pattern, two-client split, index-based proxy, etc.).
- ✅ Anti-patterns identified (v16.4's stdlib + Apache + unused keiyoushi bloat; debug builds for release; missing cleartext flag; mapper dependency).
- ⏳ **Stage 4 (video extraction) is now fully specified** but not yet implemented. The ADR + `02-video-pipeline-and-proxy.md` §7 give the complete implementation plan.
- ⚠️ **Open live verifications** (7 items in `05-cross-check-lessons.md` §6) must be done with agent-browser before finalizing endpoints/selectors: `/most-viewed` vs `/filter?sort=most-viewed`, details selectors (`#w-info` vs `.binfo`), vrf acceptance, VidTube `type` param, PNG header presence, ad-segment discrimination.

## Next steps (resume point)

1. **Verify the 7 open live items** with agent-browser (§6 of `05-cross-check-lessons.md`).
2. **Upgrade the session-08 `Anikoto.kt`**: implement `AnikotoRC4`, upgrade `SEpisode.url` to `EpisodeMeta`, add the `Source` base class, split into two clients.
3. **Implement Stage 4 (video extraction)** following ADR 03 points 6-10: Hoster discovery + parallel resolution + VidTube extractor + `LocalProxyServer` (index-based, PNG-strip, LRU cache, prefetch) + Video building (`initialized=true`) + sort+prefer + `Hoster.toHosterList`.
4. Build debug APK, user tests in Aniyomi, iterate (one fix at a time per rule §2).
5. For release: add `usesCleartextTraffic="true"`, enable R8, sign properly.

## Honest notes

- **The v3 APK being a clean debug build was a gift** — it made the deep-read straightforward (no obfuscation). The v16.4's R8 obfuscation would have made the same analysis much harder. Strategy: read v3 for logic, v16.4 for current-behavior confirmation.
- **The 3 parallel subagents worked well** — each produced a thorough structured report, and the reports cross-validated (e.g. 2-a's endpoint list matched 2-c's DTO-to-endpoint mapping). The shared worklog (`/home/z/my-project/worklog.md`) kept them coordinated.
- **No code was copied.** The analysis documents describe logic, algorithms, and techniques in prose + pseudocode + tables. Our own implementation will be written from scratch following these patterns. The RC4 key `"simple-hash"` is documented for cross-check (it's a server-side constant we must match, not "code to copy").
- **The "best method" is genuinely a synthesis**, not a copy of either reference: v3's lean build config + v16.4's current baseUrl + the index-based proxy (both) + our session-08 stubs-from-source approach + the minimal self-rolled toolkit (v3 pattern, expanded with v16.4's NetworkKt/UrlUtils + keiyoushi's ClassCastException catch).
- **The analysis took meaningful effort** (3 subagents + my synthesis) but was worth it: we now have a complete, verified blueprint for Stage 4, and our live-site research is validated. The next implementation step should be much smoother than session-08's 19-iteration build struggle.
