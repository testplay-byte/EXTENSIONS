package eu.kanade.tachiyomi.network

/**
 * Stub for PreferenceStore — provided by the Aniyomi app at runtime.
 * At compile time this lets extension code reference PreferenceStore
 * without pulling in the full app dependency.
 */
class PreferenceStore {
    fun getString(key: String, defaultValue: String = ""): String = throw Exception("Stub!")
    fun getInt(key: String, defaultValue: Int = 0): Int = throw Exception("Stub!")
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = throw Exception("Stub!")
    fun getLong(key: String, defaultValue: Long = 0L): Long = throw Exception("Stub!")
    fun getFloat(key: String, defaultValue: Float = 0f): Float = throw Exception("Stub!")
}
