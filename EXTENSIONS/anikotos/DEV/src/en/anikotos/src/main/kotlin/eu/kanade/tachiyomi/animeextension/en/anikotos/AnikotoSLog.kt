package eu.kanade.tachiyomi.animeextension.en.anikotos

import android.util.Log

/**
 * Extension logger — writes to Android logcat only (tag "AnikotoS").
 *
 * ★ session 46 (v16.30→v16.6 release): simplified to logcat-only.
 * Previous versions wrote to `Download/1118000/anikoto-*.log` files, but that required
 * `WRITE_EXTERNAL_STORAGE` (cluttering the user's device + needing a permission).
 * The extension is mature enough that logcat is sufficient for debugging.
 *
 * Usage:
 *   AnikotoSLog.i("getHosterList START")
 *   AnikotoSLog.d("GET $url")
 *   AnikotoSLog.e("resolveStreamForTask FAILED", e)
 *
 * To capture logs on-device: `adb logcat -s AnikotoS:*`
 */
object AnikotoSLog {

    private const val TAG = "AnikotoS"
    private const val EXTENSION_VERSION = "v16.1 (ext-lib 16, versionId=1)"

    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    fun d(msg: String) {
        Log.d(TAG, msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
    }

    fun e(msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, msg, throwable)
        } else {
            Log.e(TAG, msg)
        }
    }

    /**
     * Truncate a string for logging (avoids logcat 4KB line limit + keeps logs readable).
     */
    fun trunc(s: String, maxLen: Int = 60): String {
        return if (s.length <= maxLen) s else s.substring(0, maxLen) + "…(${s.length})"
    }
}
