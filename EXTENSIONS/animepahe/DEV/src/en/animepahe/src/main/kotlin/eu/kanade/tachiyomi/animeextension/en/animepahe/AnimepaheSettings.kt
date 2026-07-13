package eu.kanade.tachiyomi.animeextension.en.animepahe

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

/**
 * AnimePahe 180 settings — all preference keys, defaults, typed getters, and the settings UI.
 *
 * ## Categories (in display order)
 * 1. **Video playback** — quality, domain, sub/dub preference (at the TOP per user request)
 * 2. **Episode metadata** — thumbnails, titles, descriptions toggles (all default ON)
 *
 * ## Architecture
 * - [AnimepaheSettings] wraps a [SharedPreferences] instance and exposes typed getters.
 * - [setupPreferenceScreen] builds the settings UI.
 * - The main AnimePahe.kt class creates an instance and delegates to it.
 */
class AnimepaheSettings(private val prefs: SharedPreferences) {

    // ── Video playback ─────────────────────────────────────────────────

    /** Preferred video quality (e.g. "1080p", "720p", "360p") — used for sorting videos. */
    val preferredQuality: String
        get() = prefs.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT

    /** Preferred domain (e.g. "https://animepahe.pw") — used as baseUrl. Changing requires app restart. */
    val preferredDomain: String
        get() = prefs.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT

    /** Preferred audio: "sub" or "dub" — used for sorting videos. */
    val preferredAudio: String
        get() = prefs.getString(PREF_AUDIO_KEY, PREF_AUDIO_DEFAULT) ?: PREF_AUDIO_DEFAULT

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
                key = PREF_DOMAIN_KEY
                title = "Preferred domain"
                entries = arrayOf("animepahe.pw", "animepahe.com", "animepahe.org")
                entryValues = arrayOf(
                    "https://animepahe.pw",
                    "https://animepahe.com",
                    "https://animepahe.org",
                )
                setDefaultValue(PREF_DOMAIN_DEFAULT)
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
        internal const val PREF_DOMAIN_KEY = "pref_domain"
        internal const val PREF_DOMAIN_DEFAULT = "https://animepahe.pw"
        internal const val PREF_AUDIO_KEY = "pref_audio"
        internal const val PREF_AUDIO_DEFAULT = "sub"

        // Episode metadata
        internal const val PREF_LOAD_THUMBNAILS_KEY = "pref_load_thumbnails"
        internal const val PREF_LOAD_THUMBNAILS_DEFAULT = true
        internal const val PREF_LOAD_TITLES_KEY = "pref_load_titles"
        internal const val PREF_LOAD_TITLES_DEFAULT = true
        internal const val PREF_LOAD_DESCRIPTIONS_KEY = "pref_load_descriptions"
        internal const val PREF_LOAD_DESCRIPTIONS_DEFAULT = true
    }
}
