package com.stok.middleware.utils

import android.util.Log

/**
 * Log ke Logcat **dan** ke section log di layar (sink dari MainActivity).
 * Dipakai di client tanpa akses adb/logcat.
 */
object ScreenLog {

    /** Di-set MainActivity saat layar aktif; null saat destroy. */
    @Volatile
    var sink: ((String, String) -> Unit)? = null

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        sink?.invoke(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        sink?.invoke(tag, message)
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        if (t != null) {
            Log.e(tag, message, t)
            sink?.invoke(tag, "$message — ${t.javaClass.simpleName}: ${t.message}")
        } else {
            Log.e(tag, message)
            sink?.invoke(tag, message)
        }
    }

    /** Potong teks panjang agar RecyclerView tidak berat. */
    fun dTrunc(tag: String, prefix: String, longText: String, max: Int = 2500) {
        val msg = if (longText.length <= max) longText else longText.take(max) + "… (${longText.length} chars)"
        d(tag, "$prefix$msg")
    }
}
