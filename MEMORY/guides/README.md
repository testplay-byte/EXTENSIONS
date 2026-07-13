# guides/ — Verified How-To Guides

> **Status: VERIFIED.** Step-by-step procedures that have been followed successfully at least once.
> If a guide hasn't been validated end-to-end yet, it lives in `TEMPORARY_MEMORY/`.

## What goes here

- "How to create a new Aniyomi anime extension from `EXTENSIONS/_template`."
- "How to build an extension APK and where built APKs go."
- "How to install/test an extension APK on a device."
- "How to use agent-browser to verify a site's server list and video flow."
- "How to implement the in-app file logger (writing to `Download/1118000/`)."
- "How to set up the dev/build environment."

## Guide template (suggested)

```markdown
# Guide: <title>

> Last updated: YYYY-MM-DD · Status: Verified

## Goal
What this guide accomplishes.

## Prerequisites
What you need before starting.

## Steps
1. ...
2. ...

## Verification
How to confirm it worked.

## Troubleshooting
Common failures and fixes (link to issues-resolutions/ where relevant).
```

## Naming

`YYYY-MM-DD_short-kebab-case-title.md` (or just `howto-<topic>.md` for stable evergreen guides).

## Current contents

1. **`01-build-setup-for-ext-lib-16.md`** — the Gradle build config for ext-lib 16: `libs.versions.toml`
   (`aniyomi-lib = com.github.aniyomiorg:extensions-lib:v16`), `kei.versions.toml` (`java = 17`),
   `settings.gradle.kts`, the ONE change in `PluginExtensionLegacy.kt` (`versionName = "16.$versionCode"`),
   open verification items, build/install/test commands.
2. **`02-how-to-create-a-new-extension.md`** — per-extension file layout, `build.gradle` template,
   the full `AnimeHttpSource` source-class skeleton (all ext-lib 16 methods), `SAnime`/`SEpisode`
   filling, audio-type labeling + dedup (rule §7), `scanlator` for sub/dub (rule §8), optional
   deep-link `*UrlActivity`, build/test steps, and a verified-pitfalls checklist.
3. **`03-android-sdk-install.md`** — ★ the verified Android SDK install procedure (location
   `/home/z/my-project/ANDROID_SDK`, 458 MB, the critical `cmdline-tools → latest` rename gotcha,
   license acceptance, package install, verification, env file). Includes the ⚠️ `javac`-missing
   blocker for Gradle builds (JRE 21 present, JDK missing, no sudo).
4. **`04-build-checklist.md`** — ★ the MANDATORY pre/post-build checklist (created after
   sessions 13-16 where 3 critical build-config mistakes caused extension crashes). Covers:
   extClass correctness, stubs-not-in-APK verification, version bumping, manifest completeness,
   and the 3 critical mistakes to never repeat. **Read this BEFORE every build.**

> NOTE: guides 01-02 are derived from source verification but the actual first build is a pending
> verification item (see each guide's §6/§"open verification items"). Guide 03 is fully verified —
> the SDK is installed and functional. Guide 04 is verified — the checklist catches all known
> build issues.
