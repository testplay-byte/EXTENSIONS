package eu.kanade.tachiyomi.animeextension.en.animepahe

import android.util.Log

/**
 * Extension logger — writes to Android logcat only (tag "Animepahe").
 *
 * Same pattern as AniKoto's AnikotoLog (session 46): logcat-only, no file I/O, no permissions.
 * Capture on-device: `adb logcat -s Animepahe:*`
 */
object AnimepaheLog {

    private const val TAG = "Animepahe"

    fun i(msg: String) = Log.i(TAG, msg)
    fun d(msg: String) = Log.d(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)

    fun e(msg: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, msg, throwable) else Log.e(TAG, msg)
    }

    /** Truncate a string for logging (avoids logcat 4KB line limit). */
    fun trunc(s: String, maxLen: Int = 60): String =
        if (s.length <= maxLen) s else s.substring(0, maxLen) + "…(${s.length})"
}
