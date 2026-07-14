package eu.kanade.tachiyomi.animeextension.en.reanime

import android.util.Log

/**
 * Logcat-only logger for the Re:ANIME extension.
 *
 * All logging goes to Android logcat (tag "Reanime"). Capture with:
 *   adb logcat -s Reanime:*
 *
 * No file logging (per PROJECT_RULES §6 — removed in v16.6 for AniKoto;
 * logcat is sufficient for debugging and requires no permissions).
 */
object ReanimeLog {
    private const val TAG = "Reanime"

    fun i(msg: String) = Log.i(TAG, msg)
    fun d(msg: String) = Log.d(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, throwable: Throwable? = null) =
        if (throwable != null) Log.e(TAG, msg, throwable) else Log.e(TAG, msg)

    /** Truncate long strings to avoid logcat's 4KB line limit. */
    fun trunc(s: String, maxLen: Int = 500): String {
        return if (s.length <= maxLen) s else s.substring(0, maxLen) + "...(${s.length} chars)"
    }
}
