# Step 05 — Preferences

> **Status: TEMPLATE.** Filled in when we build the first extension.

## Purpose (from spec)
- Configure extension global settings profiles.
- Provide user controls for: **Preferred Server**, **Preferred Audio Language**, **Preferred Video
  Quality**, and **Preferred Title Language**.

## What belongs here
- `preferences-design.md` — the `setupPreferenceScreen` design: which prefs, what types (list/switch/
  edit-text/set), defaults, validation.
- `pref-keys.md` — the SharedPreferences key constants + defaults (so they're documented + stable
  across versions, and migrations are explicit).
- `pref-migration.md` — any migration logic for when keys/defaults change between versions.
- `pref-consumption.md` — where each pref is READ (in `sortHosters`/`sortVideos`/`headersBuilder`/
  `client`/`baseUrl` etc.) — a map so we don't forget to wire a pref up.

## How to do this step (process)
1. **Decide the pref set** (at minimum the 4 from the spec):
   - `PREF_SERVER` (list) — preferred hoster/server. Entries = hoster names the site offers.
   - `PREF_AUDIO` (list) — `SUB` / `HSUB` / `DUB` (rule §7 — 3 types, not 2).
   - `PREF_QUALITY` (list) — `1080` / `720` / `480` / `360` (match what the site offers).
   - `PREF_TITLE_LANG` (list) — title language preference (if the site offers multi-language titles;
     else omit).
   - Site-specific extras as needed (e.g. custom domain, SSL-ignore, mark-fillers).
2. **Implement `setupPreferenceScreen`** using `keiyoushi.utils` builders
   (`screen.addListPreference(...)`, `addSwitchPreference(...)`, `addEditTextPreference(...)`,
   `addSetPreference(...)`). See `MEMORY/research/05-...md` §6.4 for the full API.
3. **Use `preferences.delegate(key, default)`** for typed access (`MEMORY/research/05-...md` §6.3).
4. **Wire prefs into behavior**:
   - `PREF_SERVER` → `sortHosters()` (preferred server first).
   - `PREF_AUDIO` + `PREF_QUALITY` → `sortVideos()` (use `it.videoTitle.contains(audio)` /
     `it.videoTitle.contains(quality)` — NOT `it.quality`).
   - `PREF_TITLE_LANG` → `animeDetailsParse` / `searchAnimeParse` title selection.
5. **`restartRequired = true`** for prefs that need an app restart (e.g. custom domain, random UA).
6. **Document migrations** in `pref-migration.md` if changing keys/defaults between versions.

## MEMORY cross-references
- `MEMORY/research/05-keiyoushi-utils-core.md` §6 — ★ the full Preferences API (`getPreferencesLazy`,
  `delegate`, `LazyMutable`, `addListPreference`, etc.).
- `MEMORY/research/04-network-layer-and-interceptors.md` §7 — `headersBuilder()` override (where some
  prefs like custom UA get consumed).
- `MEMORY/ext-lib/02-...api-reference.md` §12 (`ConfigurableAnimeSource`), §4.3 (`sortHosters`/`sortVideos`).
- `MEMORY/PROJECT_RULES.md` §7 (audio types), §8 (scanlator for sub/dub).

## Fill-in template
```
05_PREFERENCES/
└── <EXTENSION_NAME>/
    ├── preferences-design.md
    ├── pref-keys.md
    ├── pref-migration.md
    └── pref-consumption.md
```

## Status
Template only. Populated when the first extension's preferences are designed.
