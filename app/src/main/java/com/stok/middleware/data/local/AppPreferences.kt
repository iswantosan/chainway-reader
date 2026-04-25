package com.stok.middleware.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences untuk konfigurasi: base URL, endpoint, token, RFID action/extra.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var endpointPath: String
        get() = prefs.getString(KEY_ENDPOINT_PATH, DEFAULT_ENDPOINT_PATH) ?: DEFAULT_ENDPOINT_PATH
        set(value) = prefs.edit().putString(KEY_ENDPOINT_PATH, value).apply()

    var opnameComparePath: String
        get() = prefs.getString(KEY_OPNAME_COMPARE_PATH, DEFAULT_OPNAME_COMPARE_PATH) ?: DEFAULT_OPNAME_COMPARE_PATH
        set(value) = prefs.edit().putString(KEY_OPNAME_COMPARE_PATH, value).apply()

    var staticToken: String
        get() = prefs.getString(KEY_STATIC_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STATIC_TOKEN, value).apply()

    var rfidIntentAction: String
        get() = prefs.getString(KEY_RFID_INTENT_ACTION, DEFAULT_RFID_ACTION) ?: DEFAULT_RFID_ACTION
        set(value) = prefs.edit().putString(KEY_RFID_INTENT_ACTION, value).apply()

    var rfidExtraKey: String
        get() = prefs.getString(KEY_RFID_EXTRA_KEY, DEFAULT_RFID_EXTRA_KEY) ?: DEFAULT_RFID_EXTRA_KEY
        set(value) = prefs.edit().putString(KEY_RFID_EXTRA_KEY, value).apply()

    /** Otomatis kirim antrean ke server setelah scan (debounce). */
    var autoSendScans: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEND, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SEND, value).apply()

    /**
     * Jika true, input dari kolom scan (keyboard wedge) dilaporkan sebagai RFID ke API/UI.
     * Aktifkan bila reader RFID mengisi kolom yang sama dengan barcode.
     */
    var wedgeAsRfid: Boolean
        get() = prefs.getBoolean(KEY_WEDGE_AS_RFID, false)
        set(value) = prefs.edit().putBoolean(KEY_WEDGE_AS_RFID, value).apply()

    /** JSON object string: tag_id -> nama produk. */
    var rfidNameMapJson: String
        get() = prefs.getString(KEY_RFID_NAME_MAP, "{}") ?: "{}"
        set(value) = prefs.edit().putString(KEY_RFID_NAME_MAP, value).apply()

    companion object {
        private const val PREFS_NAME = "stok_scanner_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ENDPOINT_PATH = "endpoint_path"
        private const val KEY_OPNAME_COMPARE_PATH = "opname_compare_path"
        private const val KEY_STATIC_TOKEN = "static_token"
        private const val KEY_RFID_INTENT_ACTION = "rfid_intent_action"
        private const val KEY_RFID_EXTRA_KEY = "rfid_extra_key"
        private const val KEY_AUTO_SEND = "auto_send_scans"
        private const val KEY_WEDGE_AS_RFID = "wedge_as_rfid"
        private const val KEY_RFID_NAME_MAP = "rfid_name_map_json"

        const val DEFAULT_BASE_URL = "https://pttetragi.com/public"
        const val DEFAULT_ENDPOINT_PATH = "api/rfid/scan"
        const val DEFAULT_OPNAME_COMPARE_PATH = "api/opname/compare"
        /**
         * Broadcast RFID Chainway C72 / keyboardemulator (RSCJA).
         * Key extra: [DEFAULT_RFID_EXTRA_KEY] = "data".
         * (Action `android.intent.action.scanner.RFID` tetap didengar lewat [ScannerManager] jika device memakai nama itu.)
         */
        const val DEFAULT_RFID_ACTION = "com.rscja.scanner.action.scanner.RFID"
        const val DEFAULT_RFID_EXTRA_KEY = "data"
    }
}
