package eu.kanade.tachiyomi.animeextension.en.reanime

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

/**
 * Settings for the Re:ANIME extension.
 *
 * Wraps a [SharedPreferences] instance (provided by the main Reanime.kt class)
 * and exposes typed getters + the settings UI.
 *
 * Accessed via [ConfigurableAnimeSource] — the app shows these in the
 * extension's settings screen.
 *
 * @param prefs The SharedPreferences instance (from `Injekt.get<Application>().getSharedPreferences(...)`)
 */
class ReanimeSettings(private val prefs: SharedPreferences) {

    // ── Video playback ───────────────────────────────────────────────

    /** Preferred audio: "sub" or "dub". Determines which flix servers are used. */
    val preferredAudio: String
        get() = prefs.getString(KEY_PREFERRED_AUDIO, "sub") ?: "sub"

    /** Preferred quality: used for sorting videos (best match first). */
    val preferredQuality: String
        get() = prefs.getString(KEY_PREFERRED_QUALITY, "1080") ?: "1080"

    // ── Servers ──────────────────────────────────────────────────────

    /** Whether to try HD-1 server. */
    val enableHD1: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_HD1, true)

    /** Whether to try HD-2 server. */
    val enableHD2: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_HD2, true)

    // ── Episode metadata ─────────────────────────────────────────────

    /** Whether to load episode thumbnails. */
    val loadThumbnails: Boolean
        get() = prefs.getBoolean(KEY_LOAD_THUMBS, true)

    /** WebView timeout for video extraction (seconds). */
    val webviewTimeout: Int
        get() = prefs.getString(KEY_WEBVIEW_TIMEOUT, "30")?.toIntOrNull() ?: 30

    // ── Preference screen setup ──────────────────────────────────────

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        // ── Video playback category ──
        ListPreference(screen.context).apply {
            key = KEY_PREFERRED_AUDIO
            title = "Preferred audio"
            summary = "%s"
            entries = arrayOf("Sub", "Dub")
            entryValues = arrayOf("sub", "dub")
            setDefaultValue("sub")
        }.also { screen.addPreference(it) }

        ListPreference(screen.context).apply {
            key = KEY_PREFERRED_QUALITY
            title = "Preferred quality"
            summary = "%s"
            entries = arrayOf("1080p", "720p", "480p", "360p", "Any")
            entryValues = arrayOf("1080", "720", "480", "360", "")
            setDefaultValue("1080")
        }.also { screen.addPreference(it) }

        // ── Servers category ──
        SwitchPreferenceCompat(screen.context).apply {
            key = KEY_ENABLE_HD1
            title = "HD-1 server"
            summary = "Enable the HD-1 video server"
            setDefaultValue(true)
        }.also { screen.addPreference(it) }

        SwitchPreferenceCompat(screen.context).apply {
            key = KEY_ENABLE_HD2
            title = "HD-2 server"
            summary = "Enable the HD-2 video server"
            setDefaultValue(true)
        }.also { screen.addPreference(it) }

        // ── Episode metadata category ──
        SwitchPreferenceCompat(screen.context).apply {
            key = KEY_LOAD_THUMBS
            title = "Load episode thumbnails"
            summary = "Fetch episode thumbnails from reanime.to"
            setDefaultValue(true)
        }.also { screen.addPreference(it) }

        // ── Advanced ──
        ListPreference(screen.context).apply {
            key = KEY_WEBVIEW_TIMEOUT
            title = "Video extraction timeout"
            summary = "%s seconds (for WebView-based extraction)"
            entries = arrayOf("20s", "30s", "45s", "60s")
            entryValues = arrayOf("20", "30", "45", "60")
            setDefaultValue("30")
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val KEY_PREFERRED_AUDIO = "preferred_audio"
        private const val KEY_PREFERRED_QUALITY = "preferred_quality"
        private const val KEY_ENABLE_HD1 = "enable_hd1"
        private const val KEY_ENABLE_HD2 = "enable_hd2"
        private const val KEY_LOAD_THUMBS = "load_thumbnails"
        private const val KEY_WEBVIEW_TIMEOUT = "webview_timeout"
    }
}
