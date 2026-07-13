# <Extension Name> — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Fill this in when you create
> the extension. Identity, build commands, current status, key file locations.

---

## Identity

| Field | Value | Notes |
|---|---|---|
| **Display name** | `<Name>` | Source ID = `MD5("<name> <lang>/<versionId>")` |
| **versionId** | `<N>` | STABLE once published — bumping orphans saved anime. |
| **Package** | `eu.kanade.tachiyomi.animeextension.<lang>.<name>` | |
| **extClass** | `eu.kanade.tachiyomi.animeextension.<lang>.<name>.<ClassName>` | FULL path if applicationId ≠ source package |
| **versionCode** | `<N>` | Bump per build |
| **versionName** | `<x.y>` | |
| **Target site** | `<https://site.example>` | |
| **Signing key** | `<name>-release.jks` | At `DEV/<name>-release.jks` — keep secure, in .gitignore |

## Build

```bash
source /home/z/my-project/.android-env.sh
cd /home/z/my-project/EXTENSIONS/<name>/DEV

./gradlew :src:<lang>:<name>:assembleRelease --no-daemon
# → src/<lang>/<name>/build/outputs/apk/release/...apk

./gradlew :src:<lang>:<name>:assembleDebug --no-daemon
# → src/<lang>/<name>/build/outputs/apk/debug/...apk
```

Before/after every build, follow `MEMORY/guides/04-build-checklist.md` (project-level — mandatory).

## Current status

<!-- Document what's working, what's in progress, known issues. Update as the extension matures. -->

- TODO: site analysis complete?
- TODO: catalog (popular/latest/search/filters) working?
- TODO: video servers working?
- TODO: ...

## Key file locations (relative to `EXTENSIONS/<name>/`)

| Path | What |
|---|---|
| `DEV/` | Gradle project |
| `DEV/src/<lang>/<name>/src/main/kotlin/.../<Name>.kt` | Main source class |
| `DEV/<name>-release.jks` | Signing keystore |
| `APK/` | Built APKs |
| `MEMORY/` | Knowledge base (see `MEMORY/README.md`) |

## Critical build rules (project-level — see `MEMORY/guides/04-build-checklist.md`)

1. **extClass** — full path if applicationId ≠ source package; otherwise `.ClassName`.
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE once published.
4. **Video constructor**: ALL positional args, `initialized=false`.
5. **Use inherited `client`** (CloudflareInterceptor + cookieJar).
6. **WebViewFetcher** for WAF-blocked CDNs.
7. **ProGuard**: keep ALL extension classes + `$$serializer` classes.
8. **One change at a time** (project rule §2).

## Icon preparation

★ Before building the release APK, prepare the app icon:
1. Get the icon from the user (1024×1024 PNG with transparency)
2. Auto-crop transparent borders: `img.getbbox()` → crop → resize to 1024×1024
3. Resize to all 5 mipmap densities (48/72/96/144/192)
4. Copy to `public/<name>-icon.png` for the webpage

See `HOW_TO_BUILD_EXTENSION/05-build-test-and-release.md` §5.1b for the complete Python script.
