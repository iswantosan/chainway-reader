package com.stok.middleware.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences untuk konfigurasi: Apps Script URL + token, RFID action/extra.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /** URL Web App Apps Script: https://script.google.com/macros/s/.../exec */
    var sheetsUrl: String
        get() = prefs.getString(KEY_SHEETS_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SHEETS_URL, value).apply()

    /** Token rahasia yang dicocokkan di Apps Script. */
    var sheetsToken: String
        get() = prefs.getString(KEY_SHEETS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SHEETS_TOKEN, value).apply()

    var rfidIntentAction: String
        get() = prefs.getString(KEY_RFID_INTENT_ACTION, DEFAULT_RFID_ACTION) ?: DEFAULT_RFID_ACTION
        set(value) = prefs.edit().putString(KEY_RFID_INTENT_ACTION, value).apply()

    var rfidExtraKey: String
        get() = prefs.getString(KEY_RFID_EXTRA_KEY, DEFAULT_RFID_EXTRA_KEY) ?: DEFAULT_RFID_EXTRA_KEY
        set(value) = prefs.edit().putString(KEY_RFID_EXTRA_KEY, value).apply()

    /**
     * Jika true, input dari kolom scan (keyboard wedge) dianggap RFID juga (tetap dikirim ke Sheets).
     * Tidak mempengaruhi tujuan kirim — hanya label di log lokal.
     */
    var wedgeAsRfid: Boolean
        get() = prefs.getBoolean(KEY_WEDGE_AS_RFID, false)
        set(value) = prefs.edit().putBoolean(KEY_WEDGE_AS_RFID, value).apply()

    companion object {
        private const val PREFS_NAME = "stok_scanner_prefs"
        private const val KEY_SHEETS_URL = "sheets_url"
        private const val KEY_SHEETS_TOKEN = "sheets_token"
        private const val KEY_RFID_INTENT_ACTION = "rfid_intent_action"
        private const val KEY_RFID_EXTRA_KEY = "rfid_extra_key"
        private const val KEY_WEDGE_AS_RFID = "wedge_as_rfid"

        /**
         * Broadcast RFID Chainway C72 / keyboardemulator (RSCJA).
         * Key extra: [DEFAULT_RFID_EXTRA_KEY] = "data".
         */
        const val DEFAULT_RFID_ACTION = "com.rscja.scanner.action.scanner.RFID"
        const val DEFAULT_RFID_EXTRA_KEY = "data"
    }
}
