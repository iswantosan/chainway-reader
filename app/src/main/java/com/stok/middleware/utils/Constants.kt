package com.stok.middleware.utils

/**
 * Konstanta untuk RFID scanner intent.
 * Untuk mengubah action atau extra key, bisa juga lewat Settings (disimpan di SharedPreferences).
 */
object Constants {
    const val RFID_ACTION_DEFAULT = "com.stok.middleware.RFID_SCAN"
    const val RFID_EXTRA_KEY_DEFAULT = "data"
}
