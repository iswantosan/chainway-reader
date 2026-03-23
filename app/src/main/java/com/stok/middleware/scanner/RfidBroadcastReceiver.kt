package com.stok.middleware.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stok.middleware.utils.ScreenLog

/**
 * BroadcastReceiver untuk menerima hasil scan RFID dari device.
 * Menerima action dari Settings, Chainway (com.rscja…), keyboardemulator (android.intent.action.scanner.RFID).
 * Mendukung String, CharSequence, dan ByteArray (hex/ASCII) pada extra — sesuai mode Hex di keyboardemulator.
 */
class RfidBroadcastReceiver(
    private val acceptedActions: Set<String>,
    private val extraKey: String,
    private val onScan: (String) -> Unit
) : BroadcastReceiver() {

    private fun log(tag: String, message: String) {
        ScreenLog.d(tag, message)
    }

    private fun logW(tag: String, message: String) {
        ScreenLog.w(tag, message)
    }

    private fun logE(tag: String, message: String, e: Throwable? = null) {
        ScreenLog.e(tag, message, e)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        log("[onReceive]", "ENTRY — context=${context != null}, intent=${intent != null}")
        if (context == null) {
            logW("[onReceive]", "SKIP: context is null")
            return
        }
        if (intent == null) {
            logW("[onReceive]", "SKIP: intent is null")
            return
        }
        val receivedAction = intent.action ?: ""
        val match = receivedAction in acceptedActions
        log("[onReceive]", "intent.action='$receivedAction', acceptedActions=$acceptedActions, match=$match")
        if (receivedAction !in acceptedActions) {
            logW("[onReceive]", "SKIP: action not in accepted list (ignoring this broadcast)")
            logIntentExtras(intent)
            return
        }
        try {
            val fallbackKeys = RFID_EXTRA_FALLBACK_KEYS
            val value = when {
                extraKey.isBlank() -> getEpcFromIntent(intent, fallbackKeys)
                else -> {
                    val fromConfigured = getEpcForKey(intent, extraKey)
                    log("[onReceive]", "extra '$extraKey' resolved length=${fromConfigured.length}")
                    fromConfigured.ifBlank { getEpcFromIntent(intent, fallbackKeys) }
                }
            }
            val trimmed = value.trim()
            log("[onReceive]", "value length=${value.length}, trimmed length=${trimmed.length}, trimmed='${trimmed.take(50)}${if (trimmed.length > 50) "…" else ""}'")
            if (trimmed.isEmpty()) {
                logW("[onReceive]", "SKIP: trimmed value empty — no EPC to process")
                logIntentExtras(intent)
                return
            }
            log("[onReceive]", "INVOKING onScan(epc)")
            onScan(trimmed)
            log("[onReceive]", "onScan returned OK")
        } catch (e: Exception) {
            logE("[onReceive]", "EXCEPTION", e)
            logIntentExtras(intent)
        }
    }

    private fun getEpcForKey(intent: Intent, key: String): String {
        intent.getStringExtra(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        intent.getCharSequenceExtra(key)?.trim()?.toString()?.takeIf { it.isNotEmpty() }?.let { return it }
        intent.getByteArrayExtra(key)?.let { return bytesToEpcString(it) }
        return ""
    }

    private fun getEpcFromIntent(intent: Intent, keys: List<String>): String {
        for (key in keys) {
            val v = getEpcForKey(intent, key)
            val pv = if (v.length <= 48) v else v.take(48) + "…"
            log("[onReceive]", "key='$key' -> length=${v.length}, preview='$pv'")
            if (v.isNotBlank()) return v
        }
        return ""
    }

    /** ByteArray dari scanner (mode Hex): tampilkan hex; kalau semua printable → ASCII. */
    private fun bytesToEpcString(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val printable = bytes.all { b ->
            val u = b.toInt() and 0xff
            u in 32..126 || u == 9 || u == 10 || u == 13
        }
        return if (printable) {
            String(bytes, Charsets.UTF_8).trim()
        } else {
            bytes.joinToString("") { b -> "%02X".format(b.toInt() and 0xff) }
        }
    }


    private fun logIntentExtras(intent: Intent) {
        try {
            val extras = intent.extras ?: return
            val keys = extras.keySet()?.joinToString(", ") ?: "null"
            log("[onReceive]", "Intent extras keys: $keys")
            for (key in extras.keySet() ?: emptySet<String>()) {
                val v = extras.get(key)
                val safe = when (v) {
                    is String -> v.take(100)
                    else -> v?.toString()?.take(100)
                }
                log("[onReceive]", "  extra['$key'] = $safe")
            }
        } catch (e: Exception) {
            logE("[onReceive]", "Error logging extras", e)
        }
    }

    companion object {
        /** Urutan pencarian extra jika key utama kosong (Chainway / Asset Infinity / varian). */
        private val RFID_EXTRA_FALLBACK_KEYS = listOf(
            "data",
            "barcode",
            "EPC",
            "epc",
            "rfid",
            "RFID",
            "tag",
            "TAG",
            "scan_data",
            "ScanData",
            "scannerdata"
        )
    }
}
