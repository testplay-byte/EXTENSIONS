package eu.kanade.tachiyomi.animeextension.en.miruro

import android.content.SharedPreferences

/**
 * Miruro settings — 11 preferences (our convention; a streamlined subset
 * of yuzono's 15). Per project rule §6, all settings show "Currently: %s".
 *
 * Stored in SharedPreferences "source_$id".
 */
class MiruroSettings(private val prefs: SharedPreferences) {

    companion object {
        // Mirror selection
        const val PREF_MIRROR_KEY = "preferred_mirror"
        val MIRROR_ENTRIES = arrayOf("miruro.tv", "miruro.to", "miruro.bz", "miruro.ru")
        val MIRROR_VALUES = MIRROR_ENTRIES.map { "https://www.$it" }.toTypedArray()
        const val MIRROR_DEFAULT = MIRROR_VALUES[0]   // https://www.miruro.tv

        // Preferred provider (11 native + embed servers)
        const val PREF_PROVIDER_KEY = "preferred_provider"
        // Display name (alias) — matches KNOWN_DISPLAY_NAMES in Miruro.kt
        val PROVIDER_ENTRIES = arrayOf(
            "AnimePahe", "Anikoto", "AniDao", "9Anime", "Moon",
            "Zoro", "Pewe", "Nun", "Bun", "Twin", "Cog",
        )
        val PROVIDER_VALUES = arrayOf(
            "kiwi", "bee", "bonk", "ally", "moo",
            "hop", "pewe", "nun", "bun", "twin", "cog",
        )
        const val PROVIDER_DEFAULT = "kiwi"   // AnimePahe

        // Preferred sub-type
        const val PREF_SUBTYPE_KEY = "preferred_subtype"
        val SUBTYPE_ENTRIES = arrayOf("Sub", "Dub", "Soft Sub")
        val SUBTYPE_VALUES = arrayOf("sub", "dub", "ssub")
        const val SUBTYPE_DEFAULT = "sub"

        // Preferred quality
        const val PREF_QUALITY_KEY = "preferred_quality"
        val QUALITY_ENTRIES = arrayOf("Highest Available", "1080p", "720p", "480p", "360p")
        val QUALITY_VALUES = arrayOf("0", "1080", "720", "480", "360")
        const val QUALITY_DEFAULT = "0"

        // Preferred stream type (HLS vs embed)
        const val PREF_STREAM_TYPE_KEY = "preferred_stream_type"
        val STREAM_TYPE_ENTRIES = arrayOf("HLS", "Embed", "All")
        val STREAM_TYPE_VALUES = arrayOf("hls", "embed", "all")
        const val STREAM_TYPE_DEFAULT = "hls"

        // Title display style
        const val PREF_TITLE_STYLE_KEY = "preferred_title_style"
        val TITLE_STYLE_ENTRIES = arrayOf("User Preferred", "Romaji", "English", "Native")
        val TITLE_STYLE_VALUES = arrayOf("userPreferred", "romaji", "english", "native")
        const val TITLE_STYLE_DEFAULT = "userPreferred"

        // Include all sub/dub streams (fetch sub+ssub+h-sub in addition to the preferred)
        const val PREF_INCLUDE_ALL_SUBTYPES_KEY = "include_all_subtypes"
        const val PREF_INCLUDE_ALL_SUBTYPES_DEFAULT = true

        // Include all provider streams (fetch fallback providers too)
        const val PREF_INCLUDE_ALL_PROVIDERS_KEY = "include_all_providers"
        const val PREF_INCLUDE_ALL_PROVIDERS_DEFAULT = false

        // Episode sort order
        const val PREF_EPISODE_SORT_KEY = "episode_sort_order"
        val EPISODE_SORT_ENTRIES = arrayOf("Descending (Newest First)", "Ascending (Oldest First)")
        val EPISODE_SORT_VALUES = arrayOf("descending", "ascending")
        const val EPISODE_SORT_DEFAULT = "descending"

        // Show NSFW (isAdult filter on the browse/search)
        const val PREF_INCLUDE_NSFW_KEY = "include_nsfw"
        const val PREF_INCLUDE_NSFW_DEFAULT = false

        // Strip HTML from descriptions
        const val PREF_STRIP_HTML_KEY = "strip_html_descriptions"
        const val PREF_STRIP_HTML_DEFAULT = true
    }

    var preferredMirror: String
        get() = prefs.getString(PREF_MIRROR_KEY, MIRROR_DEFAULT) ?: MIRROR_DEFAULT
        set(value) { prefs.edit().putString(PREF_MIRROR_KEY, value).apply() }

    var preferredProvider: String
        get() = prefs.getString(PREF_PROVIDER_KEY, PROVIDER_DEFAULT) ?: PROVIDER_DEFAULT
        set(value) { prefs.edit().putString(PREF_PROVIDER_KEY, value).apply() }

    var preferredSubType: String
        get() = prefs.getString(PREF_SUBTYPE_KEY, SUBTYPE_DEFAULT) ?: SUBTYPE_DEFAULT
        set(value) { prefs.edit().putString(PREF_SUBTYPE_KEY, value).apply() }

    var preferredQuality: String
        get() = prefs.getString(PREF_QUALITY_KEY, QUALITY_DEFAULT) ?: QUALITY_DEFAULT
        set(value) { prefs.edit().putString(PREF_QUALITY_KEY, value).apply() }

    var preferredStreamType: String
        get() = prefs.getString(PREF_STREAM_TYPE_KEY, STREAM_TYPE_DEFAULT) ?: STREAM_TYPE_DEFAULT
        set(value) { prefs.edit().putString(PREF_STREAM_TYPE_KEY, value).apply() }

    var preferredTitleStyle: String
        get() = prefs.getString(PREF_TITLE_STYLE_KEY, TITLE_STYLE_DEFAULT) ?: TITLE_STYLE_DEFAULT
        set(value) { prefs.edit().putString(PREF_TITLE_STYLE_KEY, value).apply() }

    var includeAllSubTypes: Boolean
        get() = prefs.getBoolean(PREF_INCLUDE_ALL_SUBTYPES_KEY, PREF_INCLUDE_ALL_SUBTYPES_DEFAULT)
        set(value) { prefs.edit().putBoolean(PREF_INCLUDE_ALL_SUBTYPES_KEY, value).apply() }

    var includeAllProviders: Boolean
        get() = prefs.getBoolean(PREF_INCLUDE_ALL_PROVIDERS_KEY, PREF_INCLUDE_ALL_PROVIDERS_DEFAULT)
        set(value) { prefs.edit().putBoolean(PREF_INCLUDE_ALL_PROVIDERS_KEY, value).apply() }

    var episodeSortOrder: String
        get() = prefs.getString(PREF_EPISODE_SORT_KEY, EPISODE_SORT_DEFAULT) ?: EPISODE_SORT_DEFAULT
        set(value) { prefs.edit().putString(PREF_EPISODE_SORT_KEY, value).apply() }

    var includeNsfw: Boolean
        get() = prefs.getBoolean(PREF_INCLUDE_NSFW_KEY, PREF_INCLUDE_NSFW_DEFAULT)
        set(value) { prefs.edit().putBoolean(PREF_INCLUDE_NSFW_KEY, value).apply() }

    var stripHtml: Boolean
        get() = prefs.getBoolean(PREF_STRIP_HTML_KEY, PREF_STRIP_HTML_DEFAULT)
        set(value) { prefs.edit().putBoolean(PREF_STRIP_HTML_KEY, value).apply() }
}
