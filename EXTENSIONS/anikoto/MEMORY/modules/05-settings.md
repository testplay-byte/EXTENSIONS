# Module: Settings

> Last updated: 2027-06-27 (session 51) · Status: VERIFIED
> Covers: preference keys, defaults, typed getters, and the settings UI.

---

## Overview

All settings are in a single file: **`AnikotoSettings.kt`**. This file is completely independent from the main `Anikoto.kt` source class — modifying settings does not require touching the main source logic.

The main class creates an `AnikotoSettings` instance and delegates:
- All preference reads via typed getters (`settings.preferredQuality`, etc.)
- The `setupPreferenceScreen()` call to build the UI

---

## File

### `AnikotoSettings.kt`

```kotlin
class AnikotoSettings(private val prefs: SharedPreferences) {
    // Typed getters
    val preferredQuality: String
    val preferredAudio: String
    val prefetchBuffer: String
    val preferredServer: String
    val enableKiwi: Boolean
    val loadThumbnails: Boolean
    val loadTitles: Boolean
    val loadDescriptions: Boolean
    val smartSearchEnabled: Boolean       // ★ session 51
    val smartSearchPhrase: String         // ★ session 51

    // Settings UI
    fun setupPreferenceScreen(screen: PreferenceScreen)

    companion object {
        // All preference keys + defaults (internal const)
    }
}
```

---

## Preference Categories

### Category 1: Playback

| Setting | Key | Type | Default | Values | Summary |
|---------|-----|------|---------|--------|---------|
| Preferred quality | `pref_quality` | ListPreference | `"720"` | 1080, 720, 480, 360 | "Currently: %s" |
| Preferred audio | `pref_audio` | ListPreference | `"SUB"` | SUB, A-DUB, H-SUB | "Currently: %s" |
| Pre-fetch buffer | `pref_buffer` | ListPreference | `"10"` | 10, 20, 30, 50, 100 | "Currently: %s" |
| Preferred server | `pref_server` | ListPreference | `"auto"` | auto, VidPlay-1, HD-1, Vidstream-2, VidCloud-1, Kiwi-Stream | "Currently: %s" |

All 4 dropdowns show **"Currently: %s"** — this displays the currently selected value so the user can see their selection at a glance.

### Category 2: Servers

| Setting | Key | Type | Default | Summary ON | Summary OFF |
|---------|-----|------|---------|-----------|-------------|
| Enable Kiwi-Stream | `pref_enable_kiwi` | SwitchPreferenceCompat | `true` | "Fetching Kiwi-Stream from external sources" | "Kiwi-Stream disabled" |

When OFF, the mapper API (`mapper.nekostream.site`) is not called — no Kiwi-Stream servers appear in the hoster list.

### Category 3: Episode metadata

| Setting | Key | Type | Default | Summary ON | Summary OFF |
|---------|-----|------|---------|-----------|-------------|
| Load episode thumbnails | `pref_load_thumbnails` | SwitchPreferenceCompat | `true` | "Fetching preview images from external sources" | "Episode thumbnails disabled (faster episode list loading)" |
| Load episode titles | `pref_load_titles` | SwitchPreferenceCompat | `true` | "Fetching episode titles from external sources" | "Using default episode numbers only" |
| Load episode descriptions | `pref_load_descriptions` | SwitchPreferenceCompat | `true` | "Fetching episode descriptions from external sources" | "Episode descriptions disabled" |

If ALL three are OFF, the metadata fetcher is skipped entirely — zero API calls, zero latency.

### Category 4: Smart Search (★ session 51)

| Setting | Key | Type | Default | Summary |
|---------|-----|------|---------|---------|
| Enable smart search | `pref_smart_search` | SwitchPreferenceCompat | `false` (OFF) | ON: "AI resolves descriptive queries and corrects spelling" / OFF: "Smart search disabled (normal keyword search only)" |
| Activation phrase | `pref_smart_search_phrase` | EditTextPreference | `"?"` | "Currently: ?" (red bold text via SpannableString) |
| Details | (info only) | Preference | — | Dynamic examples using user's actual phrase |

**Smart Search behavior:**
- **Toggle OFF** → normal keyword search only
- **Toggle ON + phrase set** → AI triggers when query starts with phrase + space
- **Toggle ON + phrase empty** → ALL searches use AI
- **Space requirement** → phrase "s" doesn't match "shock" (must be followed by space)
- **Case-insensitive** → "? naruto" and "? NarUto" both trigger

**Text color:**
- Activation phrase value shown in **red bold** (`#dc2626`) via `SpannableString` + `ForegroundColorSpan` + `StyleSpan`
- Updates dynamically when user changes the phrase
- See `updatePhraseSummary()` helper in `AnikotoSettings.kt`

**Details text (dynamic):**
- Shows user's actual phrase: "Your phrase: \"?\""
- Examples use user's phrase: "? the anime with a russian girl"
- Shows "(empty — AI used for all)" if phrase is empty
- Includes "Must be followed by a space" note

See `06-smart-search.md` for full Smart Search documentation.

---

## How Settings Are Used

### In `Anikoto.kt`

```kotlin
private val settings: AnikotoSettings by lazy {
    AnikotoSettings(preferences)
}

// Delegated getters (used throughout the source class)
private val preferredQuality: String get() = settings.preferredQuality
private val preferredAudio: String get() = settings.preferredAudio
private val prefetchBuffer: String get() = settings.prefetchBuffer
private val preferredServer: String get() = settings.preferredServer
private val enableKiwi: Boolean get() = settings.enableKiwi
private val loadThumbnails: Boolean get() = settings.loadThumbnails
private val loadTitles: Boolean get() = settings.loadTitles
private val loadDescriptions: Boolean get() = settings.loadDescriptions
private val smartSearchEnabled: Boolean get() = settings.smartSearchEnabled       // ★ session 51
private val smartSearchPhrase: String get() = settings.smartSearchPhrase           // ★ session 51

// Settings UI
override fun setupPreferenceScreen(screen: PreferenceScreen) {
    settings.setupPreferenceScreen(screen)
}
```

### Where each setting is consumed

| Setting | Used in | Effect |
|---------|---------|--------|
| `preferredQuality` | `sortVideosInternal()` | Sorts videos so preferred quality appears first |
| `preferredAudio` | `sortVideosInternal()` | Sorts videos so preferred audio type appears first |
| `prefetchBuffer` | `LocalProxyServer.prefetchCount` | % of segments to prefetch ahead (10-100) |
| `preferredServer` | `getHosterList()` | Sorts hosters so preferred server appears first |
| `enableKiwi` | `getHosterList()` | Gates PATH B (mapper API) — skip if OFF |
| `loadThumbnails` | `enrichEpisodesWithMetadata()` | Sets `SEpisode.preview_url` if ON |
| `loadTitles` | `enrichEpisodesWithMetadata()` | Sets `SEpisode.name` if ON |
| `loadDescriptions` | `enrichEpisodesWithMetadata()` | Sets `SEpisode.summary` if ON |
| `smartSearchEnabled` | `getSearchAnime()` | Gates smart search — if OFF, normal search runs |
| `smartSearchPhrase` | `getSearchAnime()` | Activation phrase for AI search (empty = all searches use AI) |

---

## How to Modify

### Add a new setting

1. **In `AnikotoSettings.kt`**:
   - Add key + default constants in `companion object`
   - Add a typed getter property
   - Add the preference UI in `setupPreferenceScreen()`

2. **In `Anikoto.kt`**:
   - Add a delegated getter: `private val newSetting get() = settings.newSetting`
   - Use it in the appropriate method

### Change a setting's default

Just change the constant in `AnikotoSettings.companion`.

### Change a setting's display

Just change the `title`, `summary`, `entries`, or `entryValues` in `setupPreferenceScreen()`.

### Add colored text to a setting summary

Use `SpannableString` with `ForegroundColorSpan` and `StyleSpan`:
```kotlin
val spannable = SpannableString(text)
spannable.setSpan(ForegroundColorSpan(Color.parseColor("#dc2626")), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
pref.summary = spannable
```

---

## See Also

- **Settings source**: `AnikotoSettings.kt`
- **Architecture**: `EXTENSIONS/anikoto/MEMORY/modules/00-architecture.md`
- **Episode metadata**: `EXTENSIONS/anikoto/MEMORY/modules/04-episode-metadata.md`
- **Smart Search**: `EXTENSIONS/anikoto/MEMORY/modules/06-smart-search.md`
