# Step 07 — Final Release

> **Status: TEMPLATE.** Filled in when the first extension is ready to ship. (The user's spec lists
> Steps 1–6 as folders and describes Step 7 in text; this folder is an **improvement** giving Step 7
> a home for release-specific docs. Logged in `MEMORY/decisions/02-...md`.)

## Purpose (from spec)
- The transition from debug iterations to building **production-ready deployment packages**.

## What belongs here
- `release-checklist.md` — the pre-release checklist (all golden-path tests pass, no debug-only code,
  versionCode bumped, changelog written, proguard rules verified).
- `signing-setup.md` — release keystore setup (`signingkey.jks` + env vars `KEY_STORE_PASSWORD` /
  `ALIAS` / `KEY_PASSWORD` — see `MEMORY/research/02-...md` §3 `PluginExtensionLegacy.kt`).
- `release-build-commands.md` — `./gradlew :src:<lang>:<name>:assembleRelease` + output location.
- `versioning.md` — the `versionCode` / `versionName = "16.$versionCode"` / `baseVersionCode` /
  `overrideVersionCode` for this release (see `MEMORY/research/02-...md` §9).
- `distribution.md` — where the release APK goes (`EXTENSIONS/<EXTENSION>/APK/` → user)
  and how the user installs it.
- `post-release.md` — post-release monitoring: known issues, planned fixes, retroactive updates to
  earlier WORKFLOW steps.

## How to do this step (process)
1. **Verify golden path** — all `test-cases.md` (step 06) pass on the latest debug build.
2. **Strip debug-only code** — remove any `Log.d` / verbose debug logs (keep the `Download/1118000/`
   file logger — it's useful in production too, just maybe less verbose).
3. **Bump `extVersionCode`** (or `overrideVersionCode` for multisrc) in `build.gradle`.
4. **Write changelog** — what changed since the last release.
5. **Set up release signing** — create/import `signing key.jks`, set env vars. (If absent,
   `PluginExtensionLegacy` falls back to debug signing — fine for personal use, not for distribution.)
6. **Build release APK:**
   ```bash
   cd EXTENSIONS/<extension_name>/DEV
   ./gradlew :src:<lang>:<name>:assembleRelease
   # output: .../apk/release/aniyomi-<lang>.<name>-v16.<code>.apk
   ```
7. **Copy to distribution:**
   ```bash
   cp .../apk/release/*.apk ../APK/                       # EXTENSIONS/<EXTENSION>/APK/ (stable)
   ```
8. **User installs the release APK** (uninstalls the debug one first if same package).
9. **Monitor** — user reports any issues → log in `post-release.md` → fix in next iteration (back to
   step 06) or next release.

## Release vs debug — key differences
| Aspect | Debug | Release |
|---|---|---|
| Signing | debug keystore (Untrusted in Aniyomi) | release `signingkey.jks` (Trusted if user adds key) |
| `isMinifyEnabled` | false | **true** (R8 strips unused code) |
| Proguard | skipped | `proguard-rules.pro` applied + keep-rules for source class |
| `versionName` | `16.<code>` | `16.<code>` (same — loader checks prefix) |
| Logs | verbose | reduced (but `Download/1118000/` logger stays) |
| `createdBy` / `appMetadata` | present | **stripped** (anti-fingerprinting, per `PluginExtensionLegacy.afterEvaluate`) |

## MEMORY cross-references
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` §7 (build commands — adapt for `assembleRelease`).
- `MEMORY/research/02-reference-extension-build-and-structure.md` §3 (`PluginExtensionLegacy.kt` —
  signing config, R8, `createdBy` stripping), §9 (versioning scheme).
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` §7 (loader compat — `versionName = "16.x"`).
- `MEMORY/PROJECT_RULES.md` §9 (APK file management — built APKs don't go in git).

## Fill-in template
```
07_FINAL_RELEASE/
└── <EXTENSION_NAME>/
    ├── release-checklist.md
    ├── signing-setup.md
    ├── release-build-commands.md
    ├── versioning.md
    ├── distribution.md
    └── post-release.md
```

## Status
Template only. Populated when the first extension is ready to ship a release build.
