package eu.kanade.tachiyomi.animeextension.en.anidb

import android.util.Log

/**
 * Logcat-only logger for the AniDB extension.
 *
 * No file I/O, no permissions. Capture with:
 *   adb logcat -s AniDB:*
 *
 * Based on the pattern established in AniKoto session 46 + AnimePahe/MKissa:
 * the extension is mature enough that logcat is sufficient for debugging.
 */
object AniDBLog {
    private const val TAG = "AniDB"
    private const val EXTENSION_VERSION = "v16.1 (build 1)"

    fun i(msg: String) = Log.i(TAG, msg)
    fun d(msg: String) = Log.d(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, throwable: Throwable? = null) =
        if (throwable != null) Log.e(TAG, msg, throwable) else Log.e(TAG, msg)

    /** Truncate long strings (URLs, tokens, response bodies) to avoid logcat's 4KB line limit. */
    fun trunc(s: String, maxLen: Int = 200): String =
        if (s.length <= maxLen) s else s.substring(0, maxLen) + "...(${s.length} chars)"
}
