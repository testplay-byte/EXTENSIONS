package eu.kanade.tachiyomi.animeextension.en.anikotos

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

/**
 * ★ Module: Settings — all preference keys, defaults, typed getters, and the settings UI.
 *
 * Extracted from AnikotoS.kt so that settings can be managed independently.
 * Modifying the settings UI or adding new preferences does not require touching the main
 * source class — just update this file.
 *
 * ## Preference categories
 * 1. **Playback** — quality, audio, buffer, server
 * 2. **Servers** — Kiwi-Stream toggle
 *
 * ## Architecture
 * - [AnikotoSSettings] wraps a [SharedPreferences] instance and exposes typed getters.
 * - [setupPreferenceScreen] builds the 2-category settings UI.
 * - The main AnikotoS.kt class creates an instance and delegates to it.
 *
 * @property prefs The SharedPreferences instance (keyed by source ID)
 */
class AnikotoSSettings(private val prefs: SharedPreferences) {

    // ── Typed getters ──────────────────────────────────────────────────

    /** Preferred video quality resolution string (e.g. "720") */
    val preferredQuality: String
        get() = prefs.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT

    /** Preferred audio type label (e.g. "SUB", "A-DUB", "H-SUB") */
    val preferredAudio: String
        get() = prefs.getString(PREF_AUDIO_KEY, PREF_AUDIO_DEFAULT) ?: PREF_AUDIO_DEFAULT

    /** Prefetch buffer percentage (e.g. "10") */
    val prefetchBuffer: String
        get() = prefs.getString(PREF_BUFFER_KEY, PREF_BUFFER_DEFAULT) ?: PREF_BUFFER_DEFAULT

    /** Preferred server name (e.g. "auto", "VidPlay-1") */
    val preferredServer: String
        get() = prefs.getString(PREF_SERVER_KEY, PREF_SERVER_DEFAULT) ?: PREF_SERVER_DEFAULT

    /** Whether Kiwi-Stream server discovery is enabled (default: true) */
    val enableKiwi: Boolean
        get() = prefs.getBoolean(PREF_ENABLE_KIWI_KEY, PREF_ENABLE_KIWI_DEFAULT)

    // ── Settings UI ────────────────────────────────────────────────────

    /**
     * Build the settings preference screen with 2 categories.
     *
     * Categories:
     * 1. **Playback** — quality, audio, buffer, server (all with "Currently: %s")
     * 2. **Servers** — Kiwi-Stream toggle
     *
     * All dropdowns show "Currently: %s" so the user can see the current value.
     */
    fun setupPreferenceScreen(screen: PreferenceScreen) {

        // ── Category 1: Playback ────────────────────────────────────────
        PreferenceCategory(screen.context).apply {
            title = "Playback"
            screen.addPreference(this)

            ListPreference(context).apply {
                key = PREF_QUALITY_KEY
                title = "Preferred quality"
                entries = arrayOf("1080p", "720p", "480p", "360p")
                entryValues = arrayOf("1080", "720", "480", "360")
                setDefaultValue(PREF_QUALITY_DEFAULT)
                summary = "Currently: %s"
            }.also(::addPreference)

            ListPreference(context).apply {
                key = PREF_AUDIO_KEY
                title = "Preferred audio"
                entries = arrayOf("Sub", "Dub", "Hardsub")
                entryValues = arrayOf("SUB", "A-DUB", "H-SUB")
                setDefaultValue(PREF_AUDIO_DEFAULT)
                summary = "Currently: %s"
            }.also(::addPreference)

            ListPreference(context).apply {
                key = PREF_BUFFER_KEY
                title = "Pre-fetch buffer"
                entries = arrayOf("10%", "20%", "30%", "50%", "100%")
                entryValues = arrayOf("10", "20", "30", "50", "100")
                setDefaultValue(PREF_BUFFER_DEFAULT)
                summary = "Currently: %s"
            }.also(::addPreference)

            ListPreference(context).apply {
                key = PREF_SERVER_KEY
                title = "Preferred server"
                entries = arrayOf("Auto", "VidPlay-1", "HD-1", "Vidstream-2", "VidCloud-1", "Kiwi-Stream")
                entryValues = arrayOf("auto", "VidPlay-1", "HD-1", "Vidstream-2", "VidCloud-1", "Kiwi-Stream")
                setDefaultValue(PREF_SERVER_DEFAULT)
                summary = "Currently: %s"
            }.also(::addPreference)
        }

        // ── Category 2: Servers ─────────────────────────────────────────
        PreferenceCategory(screen.context).apply {
            title = "Servers"
            screen.addPreference(this)

            SwitchPreferenceCompat(context).apply {
                key = PREF_ENABLE_KIWI_KEY
                title = "Enable Kiwi-Stream"
                summaryOn = "Fetching Kiwi-Stream from external sources"
                summaryOff = "Kiwi-Stream disabled"
                setDefaultValue(PREF_ENABLE_KIWI_DEFAULT)
            }.also(::addPreference)
        }
    }

    companion object {
        // ── Preference keys + defaults ──────────────────────────────────
        // All keys are private — access through typed getters only.

        // Playback
        internal const val PREF_QUALITY_KEY = "pref_quality"
        internal const val PREF_QUALITY_DEFAULT = "720"
        internal const val PREF_AUDIO_KEY = "pref_audio"
        internal const val PREF_AUDIO_DEFAULT = "SUB"
        internal const val PREF_BUFFER_KEY = "pref_buffer"
        internal const val PREF_BUFFER_DEFAULT = "10"
        internal const val PREF_SERVER_KEY = "pref_server"
        internal const val PREF_SERVER_DEFAULT = "auto"

        // Servers
        internal const val PREF_ENABLE_KIWI_KEY = "pref_enable_kiwi"
        internal const val PREF_ENABLE_KIWI_DEFAULT = true
    }
}
