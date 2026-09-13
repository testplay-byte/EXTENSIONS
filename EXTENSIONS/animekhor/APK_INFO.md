# AnimeKhor 180 — Extension APK Information Sheet

> Generated: 2026-09-13 (session animekhor-01) · By: Confused_Creature (180)
> Current version: v16.1 (versionCode=1) — **DEBUG test build** (branch `ext/animekhor`, unpublished)

---

## APK Details

| Property | Value |
|----------|-------|
| **File name** | `aniyomi-en.animekhor180-v16.1-debug.apk` |
| **File size** | 110,953 B (~108 KB) |
| **SHA-256 (file)** | `9fd09877c7bdd934a1d821696d9e0d2ec708abeddb8d952e10714a1f0c90f4a9` |
| **Build type** | DEBUG (debug keystore — NOT for distribution) |
| **App label** | AnimeKhor 180 |
| **Package name** | `eu.kanade.tachiyomi.animeextension.en.animekhor180` |
| **Version** | `16.1` (versionCode=1) |
| **Extension versionId** | `1` (STABLE once first published — do NOT change) |
| **Extension class** | `eu.kanade.tachiyomi.animeextension.en.animekhor.AnimeKhor` (FULL path, no leading dot) |
| **ext-lib version** | 16 (versionName must start with "16.") |
| **Language** | English (en) |
| **NSFW** | false |
| **Min / Target / Compile SDK** | 21 / 34 / 34 |

## Verification (session animekhor-01)

| Check | Result |
|-------|--------|
| Icons | 5 densities (mdpi → xxxhdpi) ✅ (official AnimeKhor logo) |
| `AnimeKhor` class in dex | Present ✅ |
| All 8 extractors in dex | Dailymotion/Okru/Rumble/StreamWish/VidHide/Dood/Vidara/Abyss ✅ |
| PlaylistUtils + JsUnpacker + Filters | Present ✅ |
| Manifest meta-data keys | class/nsfw/versionId all present ✅ |
| baseUrl in dex | `animekhor.org` ✅ |
| Selector markers (`entry-title`, `eplister`) | Present ✅ |
| CI run | test4 (run #31, v16.1-ak-test4) SUCCESS after 3 compile-fix rounds |

## ⚠️ Before any RELEASE (only with explicit user approval)

1. Create `animekhor-release.jks` + add `ANIMEKHOR_KEYSTORE_BASE64` secret + release build step
   (pattern: anikoto's build.gradle.kts signingConfig + release.yml "Restore AniKoto keystore").
2. ONLY THEN bump to a release version + tag — debug builds never go to the dist repo.
3. Merge order: user approves → merge `ext/animekhor` into main → tag release.

## Build Commands (CI only — never local)

```bash
curl -s -X POST -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/testplay-byte/EXTENSIONS/actions/workflows/release.yml/dispatches \
  -d '{"ref":"ext/animekhor","inputs":{"tag":"v16.1-ak-testN","publish":"false"}}'
# artifact: test-build-apks-v16.1-ak-testN → aniyomi-en.animekhor180-v16.1-debug.apk
```

---

*This document contains all critical information about the AnimeKhor 180 extension APK v16.1 (debug).*
