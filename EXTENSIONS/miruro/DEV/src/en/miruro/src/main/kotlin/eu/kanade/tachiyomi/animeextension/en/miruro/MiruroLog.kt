package eu.kanade.tachiyomi.animeextension.en.miruro

import android.util.Log

/**
 * Miruro logcat-only logger (project rule §6 — no file logging, no permissions).
 *
 * Capture with: `adb logcat -s Miruro:*`
 */
object MiruroLog {
    private const val TAG = "Miruro"

    fun i(msg: String) = Log.i(TAG, msg)
    fun d(msg: String) = Log.d(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, t: Throwable? = null) =
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)

    /** Truncate long strings to avoid logcat's 4KB line limit. */
    fun trunc(s: String, maxLen: Int = 80): String =
        if (s.length <= maxLen) s else s.substring(0, maxLen) + "…(${s.length})"
}
