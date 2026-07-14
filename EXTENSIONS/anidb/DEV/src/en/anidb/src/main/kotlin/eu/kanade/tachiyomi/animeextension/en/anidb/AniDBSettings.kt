package eu.kanade.tachiyomi.animeextension.en.anidb

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

/**
 * AniDB 180 settings — preference keys, defaults, typed getters, and the settings UI.
 *
 * ## Categories (in display order)
 * 1. **Video playback** — quality, sub/dub preference (at the TOP per the convention)
 * 2. **Episode metadata** — thumbnails toggle (default ON; off = faster episode list)
 *
 * ## Architecture
 * - [AniDBSettings] wraps a [SharedPreferences] instance and exposes typed getters.
 * - [setupPreferenceScreen] builds the settings UI.
 * - The main AniDB.kt class creates an instance and delegates to it.
 */
class AniDBSettings(private val prefs: SharedPreferences) {

    // ── Video playback ─────────────────────────────────────────────────

    /** Preferred video quality (e.g. "1080p", "720p") — used for sorting videos. */
    val preferredQuality: String
        get() = prefs.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT

    /** Preferred audio: "sub" or "dub" — used for sorting videos. */
    val preferredAudio: String
        get() = prefs.getString(PREF_AUDIO_KEY, PREF_AUDIO_DEFAULT) ?: PREF_AUDIO_DEFAULT

    // ── Episode display ────────────────────────────────────────────────

    /** Whether to mark filler episodes in the scanlator field (default: true). */
    val markFiller: Boolean
        get() = prefs.getBoolean(PREF_MARK_FILLER_KEY, PREF_MARK_FILLER_DEFAULT)

    // ── Settings UI ────────────────────────────────────────────────────

    fun setupPreferenceScreen(screen: PreferenceScreen) {

        // ── Category 1: Video playback ────────────────────────────────
        PreferenceCategory(screen.context).apply {
            title = "Video playback"
            screen.addPreference(this)

            ListPreference(context).apply {
                key = PREF_QUALITY_KEY
                title = "Preferred quality"
                entries = arrayOf("1080p", "720p", "480p", "360p")
                entryValues = arrayOf("1080p", "720p", "480p", "360p")
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
        }

        // ── Category 2: Episode display ───────────────────────────────
        PreferenceCategory(screen.context).apply {
            title = "Episode display"
            screen.addPreference(this)

            SwitchPreferenceCompat(context).apply {
                key = PREF_MARK_FILLER_KEY
                title = "Mark filler episodes"
                summaryOn = "Filler episodes are tagged in the scanlator field"
                summaryOff = "Filler episodes are not specially marked"
                setDefaultValue(PREF_MARK_FILLER_DEFAULT)
            }.also(::addPreference)
        }
    }

    companion object {
        // Video playback
        internal const val PREF_QUALITY_KEY = "pref_quality"
        internal const val PREF_QUALITY_DEFAULT = "1080p"
        internal const val PREF_AUDIO_KEY = "pref_audio"
        internal const val PREF_AUDIO_DEFAULT = "sub"

        // Episode display
        internal const val PREF_MARK_FILLER_KEY = "pref_mark_filler"
        internal const val PREF_MARK_FILLER_DEFAULT = true
    }
}
