# UniQuestream 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, key file locations.

---

## Identity

| Field | Value | Notes |
|---|---|---|
| **Display name** | `UniQuestream` | Source ID = `MD5("uniquestream en/1")` |
| **versionId** | `1` | STABLE once published |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.uniquestream180` | |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.uniquestream.UniQuestream` | FULL path (applicationId ≠ source package) |
| **versionCode** | `1` | Bump per build |
| **versionName** | `16.1` | |
| **Target site** | `https://anime.uniquestream.net` | |
| **Signing key** | `uniquestream-release.jks` | At `DEV/uniquestream-release.jks` — keep secure |

## Build

```bash
# NOTE: No Android SDK/JDK installed in this environment.
# Builds must happen in the user's environment or CI.
cd /home/z/my-project/EXTENSIONS/uniquestream/DEV

./gradlew :src:en:uniquestream:assembleDebug --no-daemon
# → src/en/uniquestream/build/outputs/apk/debug/aniyomi-en.uniquestream-v16.1-debug.apk

./gradlew :src:en:uniquestream:assembleRelease --no-daemon
# → src/en/uniquestream/build/outputs/apk/release/aniyomi-en.uniquestream-v16.1-release.apk
```

Before/after every build, follow `MEMORY/guides/04-build-checklist.md` (project-level — mandatory).

## Current status

- [x] Site analysis complete (see `MEMORY/sites/2027-07-10_uniquestream-site-analysis.md`)
- [x] All API endpoints documented and verified
- [ ] Gradle project scaffolded
- [ ] Catalog: popular, latest, search implemented
- [ ] Details + episodes implemented
- [ ] Video playback (HLS stream) implemented
- [ ] Release build

## Key Architecture Decisions

1. **Pure API extension** — no HTML parsing. All data from REST API at `/api/v1/`.
2. **Single CDN hoster** — `get2.mediacache.cc` with signed HLS URLs.
3. **Sub/Dub** — determined by `audio_locales` on each episode.
   - `en-US` in audio_locales → DUB available
   - SUB always available (ja-JP audio + hardsubs)
4. **Seasons flattening** — all seasons' episodes flattened into one list.
5. **No auth required** — all API endpoints work without login.
6. **Signed URLs** — expire in ~10 min; must fetch fresh at play time.
7. **Cloudflare Turnstile** — on frontend only, NOT on API. Extension unaffected.

## Key file locations (relative to `EXTENSIONS/uniquestream/`)

| Path | What |
|---|---|
| `DEV/` | Gradle project |
| `DEV/src/en/uniquestream/src/eu/kanade/tachiyomi/animeextension/en/uniquestream/UniQuestream.kt` | Main source class |
| `DEV/uniquestream-release.jks` | Signing keystore |
| `APK/` | Built APKs |
| `MEMORY/` | Knowledge base |
| `MEMORY/sites/2027-07-10_uniquestream-site-analysis.md` | Complete site analysis |

## Critical build rules (project-level — see `MEMORY/guides/04-build-checklist.md`)

1. **extClass** — full path: `eu.kanade.tachiyomi.animeextension.en.uniquestream.UniQuestream`
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE once published.
4. **Video constructor**: ALL 14 named args, `initialized = false`.
5. **Use inherited `client`** (CloudflareInterceptor + cookieJar).
6. **ProGuard**: keep ALL extension classes + `$$serializer` classes.
7. **One change at a time** (project rule §2).
