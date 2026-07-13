# 01 — APK Overview: Anikoto Reference Extensions (v3 vs v16.4)

> Last updated: 2026-06-22 (session 10) · Status: VERIFIED (decompiled with jadx 1.5.3, cross-checked)
> Per project rule §1: this is a **cross-check / understanding** record. No code copied.

Two reference APKs were analyzed. **Both are ext-lib 16 Anikoto extensions with the same core
architecture** (Source base + LocalProxyServer + RC4 + VidTube extractor), but they differ in
author, build type, site domain, and bundling strategy.

## At-a-glance comparison

| Attribute | **v3** (`anikoto-by-1118000-v3.apk`) | **v16.4** (`anikoto-refrence-v16.4.apk`) |
|---|---|---|
| Size | 250 KB | 1.1 MB (single 3.3 MB `classes.dex`) |
| Package | `eu.kanade.tachiyomi.animeextension.en.anikotofinal` | `eu.kanade.tachiyomi.animeextension.all.anikoto` |
| versionCode / versionName | 1 / `16.1` | 4 / `16.4` |
| ext-lib major | 16 (libVersion=16.0, loader-accepted) | 16 (libVersion=16.0, loader-accepted) |
| Label | "Aniyomi: Anikoto by 1118000" | "Anikoto" |
| Author meta | (none) | `salmanbappi` |
| versionId meta | 2 | 2 |
| NSFW meta | 0 | 0 |
| Source class | `.AnikotoFinal` → `…anikotofinal.AnikotoFinal` | `.Anikoto` → `…all.anikoto.Anikoto` |
| minSdk | 26 | 21 |
| targetSdk / compileSdk | 34 / 34 | 34 / 34 |
| Build type | **debug** (`android:debuggable="true"`) | **release** (R8-obfuscated, signed) |
| `usesCleartextTraffic` | not set (debug default allows) | `true` (needed for localhost HTTP proxy in release) |
| DEX files | 4 (`classes.dex` 46KB + `classes4.dex` 189KB bulk) | 1 (`classes.dex` 3.3 MB) |
| Kotlin stdlib bundled | ❌ no (`kotlin.stdlib.default.dependency=false` effective) | ✅ yes (all `kotlin/*.kotlin_builtins`) |
| Apache Commons bundled | ❌ no | ✅ yes (`org.apache.commons.lang3` + `text`, 722 files) |
| keiyoushi/utils bundled | ❌ no | ✅ yes (75 files) — **but unused by Anikoto class itself** |
| `extensions.utils` (custom toolkit) | 26 files | 43 files (added `NetworkKt`, `UrlUtils`, more prefs) |
| R8 resource obfuscation | no (debug) | yes (resources renamed `9w.png`, `FS.png`, etc.) |
| jadx readability | ★★★★★ (clean, non-obfuscated) | ★★★☆☆ (class names kept via keep rules, but R8-shrunk bodies) |
| `baseUrl` | `https://anikoto.cz` | `https://anikototv.to` ← **site migrated** |
| `lang` | `en` | `all` (multi-language) |
| Mapper API (`mapper.nekostream.site`) | ✅ used (optional discovery path) | ❌ **dropped** |
| LocalProxyServer | 1236 lines | 1235 lines (near-identical) |
| Main source class LOC | 1346 | 911 (refactored, leaner) |

## Key takeaways

1. **Same architecture, two iterations.** v16.4 is a refined evolution of v3 by a different author.
   The core (Source → LocalProxyServer → VidTube → Hoster) is unchanged. v16.4 dropped the
   third-party mapper fallback and refactored the main class smaller.

2. **The site migrated domains**: `anikoto.cz` (v3) → `anikototv.to` (v16.4). Our live-site
   analysis in `EXTENSIONS/anikoto/MEMORY/sites/` already uses `anikototv.to`, so **our research is current**;
   v3's baseUrl is stale. ★ Always use `anikototv.to`.

3. **v3 is the better reference for READING** (clean debug build, non-obfuscated, 250 KB).
   v16.4 is the better reference for CURRENT BEHAVIOR (matches the live site, has the latest
   refactors). Read both: v3 to understand the logic, v16.4 to confirm what's current.

4. **v3's build config is LEANER and better.** v16.4's 3.3 MB single-DEX is pure bloat — it
   bundles the Kotlin stdlib, Apache Commons, and keiyoushi/utils that are NOT needed (the host
   Aniyomi app provides stdlib + keiyoushi at runtime). v3's 250 KB (all deps `compileOnly`,
   stdlib not bundled) is the correct approach. **Our build at 80 KB is even leaner** (we don't
   bundle the ext-lib stubs into the release either). See `decisions/03-best-method-to-build-extensions.md`.

5. **RC4 key `"simple-hash"` is identical in both** — the site's vrf obfuscation hasn't changed
   across the domain migration. See `02-video-pipeline-and-proxy.md` §3.

6. **`usesCleartextTraffic="true"` is REQUIRED for release builds** that run a localhost HTTP
   proxy (the LocalProxyServer serves `http://127.0.0.1:PORT/...`). Debug builds allow cleartext
   by default; release builds on Android 9+ (API 28+) block it unless this flag is set. v3
   (debug) didn't need it; v16.4 (release) does. ★ Our release build must add this.

## Decompilation provenance

- Tool: `jadx 1.5.3` (installed at `/home/z/my-project/.tools/jadx/`)
- Output: `/home/z/my-project/.tools/apk-out/v3/` and `/home/z/my-project/.tools/apk-out/v16-4/`
- Command: `jadx -q --no-debug-info --no-inline-anonymous --no-replace-consts -d <out> <apk>`
- For v3's coroutine bodies (jadx dumped some as "Method dump skipped"), re-decompiled with
  `jadx --show-bad-code` and cross-checked against v16.4's cleanly-decompiled equivalents.

## Related docs in this folder

- `02-video-pipeline-and-proxy.md` — ★ the Hoster flow + LocalProxyServer + RC4 + mapper (the heart)
- `03-catalog-and-dtos.md` — catalog endpoints, filters, DTOs, EpisodeMeta encoding
- `04-toolkit-and-utils.md` — the custom `extensions.utils` toolkit vs keiyoushi/utils
- `05-cross-check-lessons.md` — what we LEARN (patterns to adopt/avoid) + verification against our live-site research

## Related docs elsewhere

- `EXTENSIONS/anikoto/MEMORY/sites/` — our own live-site research (the source of truth for current behavior)
- `MEMORY/decisions/03-best-method-to-build-extensions.md` — the ADR informed by this analysis
- `SHARED/APK_REFERENCE/README.md` — the APK folder (updated with both rows)
