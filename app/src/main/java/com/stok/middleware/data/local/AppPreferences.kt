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

    var staticToken: String
        get() = prefs.getString(KEY_STATIC_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STATIC_TOKEN, value).apply()

    var rfidIntentAction: String
        get() = prefs.getString(KEY_RFID_INTENT_ACTION, DEFAULT_RFID_ACTION) ?: DEFAULT_RFID_ACTION
        set(value) = prefs.edit().putString(KEY_RFID_INTENT_ACTION, value).apply()

    var rfidExtraKey: String
        get() = prefs.getString(KEY_RFID_EXTRA_KEY, DEFAULT_RFID_EXTRA_KEY) ?: DEFAULT_RFID_EXTRA_KEY
        set(value) = prefs.edit().putString(KEY_RFID_EXTRA_KEY, value).apply()

    companion object {
        private const val PREFS_NAME = "stok_scanner_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ENDPOINT_PATH = "endpoint_path"
        private const val KEY_STATIC_TOKEN = "static_token"
        private const val KEY_RFID_INTENT_ACTION = "rfid_intent_action"
        private const val KEY_RFID_EXTRA_KEY = "rfid_extra_key"

        const val DEFAULT_BASE_URL = "https://pttetragi.com/public"
        const val DEFAULT_ENDPOINT_PATH = "api/rfid/scan"
        /**
         * Broadcast RFID Chainway C72 / keyboardemulator (RSCJA).
         * Key extra: [DEFAULT_RFID_EXTRA_KEY] = "data".
         * (Action `android.intent.action.scanner.RFID` tetap didengar lewat [ScannerManager] jika device memakai nama itu.)
         */
        const val DEFAULT_RFID_ACTION = "com.rscja.scanner.action.scanner.RFID"
        const val DEFAULT_RFID_EXTRA_KEY = "data"
    }
}
