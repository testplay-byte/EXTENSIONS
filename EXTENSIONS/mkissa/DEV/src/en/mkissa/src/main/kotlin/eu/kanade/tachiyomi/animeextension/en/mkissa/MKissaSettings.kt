package eu.kanade.tachiyomi.animeextension.en.mkissa

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

/**
 * MKissa 180 settings — all preference keys, defaults, typed getters, and the settings UI.
 *
 * ## Categories (in display order — matches the AnimePahe pattern for consistency)
 * 1. **Video playback** — quality, audio (sub/dub), title style (at the TOP per user request)
 * 2. **Episode metadata** — thumbnails, titles, descriptions toggles (all default ON)
 *
 * ## Conventions (★ copy these exactly — match AniKoto + AnimePahe)
 * - All dropdowns show `summary = "Currently: %s"` so the user sees their current selection.
 * - Metadata toggles use "external sources" (NEVER name specific APIs like MAL/AniList/Kitsu).
 * - Title format for episodes: "EP N - title" (NOT "Episode N - title").
 */
class MKissaSettings(private val prefs: SharedPreferences) {

    init {
        // ★ Settings migration: if the stored server set doesn't match the current SERVER_NAMES,
        // reset to default. This handles upgrades from v16.10-v16.15 (14 servers) → v16.16+ (6) → v16.20 (7 with Ak).
        val currentServers = prefs.getStringSet(PREF_SERVERS_KEY, null)
        if (currentServers != null && currentServers.size != SERVER_NAMES.size) {
            prefs.edit().putStringSet(PREF_SERVERS_KEY, PREF_SERVERS_DEFAULT).apply()
        }
    }

    // ── Video playback ─────────────────────────────────────────────────

    /** Preferred video quality (e.g. "1080p", "720p", "360p") — used for sorting videos. */
    val preferredQuality: String
        get() = prefs.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT

    /** Preferred audio: "sub" or "dub" — used for sorting videos + filtering episodes. */
    val preferredAudio: String
        get() = prefs.getString(PREF_AUDIO_KEY, PREF_AUDIO_DEFAULT) ?: PREF_AUDIO_DEFAULT

    /** Title display style: "romaji" (default), "eng", or "native". */
    val titleStyle: String
        get() = prefs.getString(PREF_TITLE_STYLE_KEY, PREF_TITLE_STYLE_DEFAULT) ?: PREF_TITLE_STYLE_DEFAULT

    // ── Episode metadata ───────────────────────────────────────────────

    /** Whether to load episode thumbnails from external sources (default: true) */
    val loadThumbnails: Boolean
        get() = prefs.getBoolean(PREF_LOAD_THUMBNAILS_KEY, PREF_LOAD_THUMBNAILS_DEFAULT)

    /** Whether to load episode titles from external sources (default: true) */
    val loadTitles: Boolean
        get() = prefs.getBoolean(PREF_LOAD_TITLES_KEY, PREF_LOAD_TITLES_DEFAULT)

    /** Whether to load episode descriptions from external sources (default: true) */
    val loadDescriptions: Boolean
        get() = prefs.getBoolean(PREF_LOAD_DESCRIPTIONS_KEY, PREF_LOAD_DESCRIPTIONS_DEFAULT)

    // ── Server toggles (video playback) ─────────────────────────────

    /** Set of enabled server names (lowercase). Empty = all enabled. */
    val enabledServers: Set<String>
        get() = prefs.getStringSet(PREF_SERVERS_KEY, PREF_SERVERS_DEFAULT) ?: PREF_SERVERS_DEFAULT

    /** Preferred server name (lowercase) — picked first. Empty = "Site Default" (priority-based). */
    val preferredServer: String
        get() = prefs.getString(PREF_PREFERRED_SERVER_KEY, PREF_PREFERRED_SERVER_DEFAULT) ?: PREF_PREFERRED_SERVER_DEFAULT

    /**
     * Build the settings preference screen.
     * ★ Video playback is FIRST (per user request), Episode metadata is SECOND.
     */
    fun setupPreferenceScreen(screen: PreferenceScreen) {

        // ── Category 1: Video playback (at the TOP) ────────────────────
        PreferenceCategory(screen.context).apply {
            title = "Video playback"
            screen.addPreference(this)

            ListPreference(context).apply {
                key = PREF_QUALITY_KEY
                title = "Preferred quality"
                entries = arrayOf("1080p", "720p", "360p")
                entryValues = arrayOf("1080p", "720p", "360p")
                setDefaultValue(PREF_QUALITY_DEFAULT)
                summary = "Currently: %s"
            }.also(::addPreference)

            ListPreference(context).apply {
                key = PREF_AUDIO_KEY
                title = "Preferred audio"
                entries = arrayOf("Sub", "Dub")
                entryValues = arrayOf("sub", "dub")
                setDefaultValue(PREF_AUDIO_DEFAULT)
                summary = "Currently: %s"
            }.also(::addPreference)

            ListPreference(context).apply {
                key = PREF_TITLE_STYLE_KEY
                title = "Title style"
                entries = arrayOf("Romaji", "English", "Native")
                entryValues = arrayOf("romaji", "eng", "native")
                setDefaultValue(PREF_TITLE_STYLE_DEFAULT)
                summary = "Currently: %s"
            }.also(::addPreference)

            ListPreference(context).apply {
                key = PREF_PREFERRED_SERVER_KEY
                title = "Preferred server"
                entries = arrayOf("Site Default", "Fm-Hls", "Uni", "Mp4", "Ok", "Vn-Hls", "Luf-Mp4", "Ak")
                entryValues = arrayOf("", "fm-hls", "uni", "mp4", "ok", "vn-hls", "luf-mp4", "ak")
                setDefaultValue(PREF_PREFERRED_SERVER_DEFAULT)
                summary = "Currently: %s"
            }.also(::addPreference)
        }

        // ── Category 2: Servers (video playback) ───────────────────────
        PreferenceCategory(screen.context).apply {
            title = "Servers"
            screen.addPreference(this)

            androidx.preference.MultiSelectListPreference(context).apply {
                key = PREF_SERVERS_KEY
                title = "Enable/Disable servers"
                summary = "Select which video servers to show in the episode list"
                entries = SERVER_NAMES
                entryValues = SERVER_NAMES.map { it.lowercase() }.toTypedArray()
                setDefaultValue(PREF_SERVERS_DEFAULT)
            }.also(::addPreference)
        }

        // ── Category 2: Episode metadata ───────────────────────────────
        PreferenceCategory(screen.context).apply {
            title = "Episode metadata"
            screen.addPreference(this)

            SwitchPreferenceCompat(context).apply {
                key = PREF_LOAD_THUMBNAILS_KEY
                title = "Load episode thumbnails"
                summaryOn = "Fetching preview images from external sources"
                summaryOff = "Episode thumbnails disabled (faster episode list loading)"
                setDefaultValue(PREF_LOAD_THUMBNAILS_DEFAULT)
            }.also(::addPreference)

            SwitchPreferenceCompat(context).apply {
                key = PREF_LOAD_TITLES_KEY
                title = "Load episode titles"
                summaryOn = "Fetching episode titles from external sources"
                summaryOff = "Using default episode numbers only"
                setDefaultValue(PREF_LOAD_TITLES_DEFAULT)
            }.also(::addPreference)

            SwitchPreferenceCompat(context).apply {
                key = PREF_LOAD_DESCRIPTIONS_KEY
                title = "Load episode descriptions"
                summaryOn = "Fetching episode descriptions from external sources"
                summaryOff = "Episode descriptions disabled"
                setDefaultValue(PREF_LOAD_DESCRIPTIONS_DEFAULT)
            }.also(::addPreference)
        }
    }

    companion object {
        // Video playback
        internal const val PREF_QUALITY_KEY = "pref_quality"
        internal const val PREF_QUALITY_DEFAULT = "1080p"
        internal const val PREF_AUDIO_KEY = "pref_audio"
        internal const val PREF_AUDIO_DEFAULT = "sub"
        internal const val PREF_TITLE_STYLE_KEY = "pref_title_style"
        internal const val PREF_TITLE_STYLE_DEFAULT = "romaji"

        // Episode metadata
        internal const val PREF_LOAD_THUMBNAILS_KEY = "pref_load_thumbnails"
        internal const val PREF_LOAD_THUMBNAILS_DEFAULT = true
        internal const val PREF_LOAD_TITLES_KEY = "pref_load_titles"
        internal const val PREF_LOAD_TITLES_DEFAULT = true
        internal const val PREF_LOAD_DESCRIPTIONS_KEY = "pref_load_descriptions"
        internal const val PREF_LOAD_DESCRIPTIONS_DEFAULT = true

        // Server toggles — the REAL servers that the API returns
        internal const val PREF_SERVERS_KEY = "pref_servers"
        internal val SERVER_NAMES = arrayOf("Fm-Hls", "Uni", "Mp4", "Ok", "Vn-Hls", "Luf-Mp4", "Ak")
        internal val PREF_SERVERS_DEFAULT = SERVER_NAMES.map { it.lowercase() }.toSet() // all enabled by default
        internal const val PREF_PREFERRED_SERVER_KEY = "pref_preferred_server"
        internal const val PREF_PREFERRED_SERVER_DEFAULT = "" // "" = Site Default (priority-based)
    }
}
