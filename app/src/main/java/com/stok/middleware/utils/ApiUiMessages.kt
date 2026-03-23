package com.stok.middleware.utils

/**
 * Menyamakan teks dari API (sering berbahasa Inggris / kata "Barcode") untuk snackbar & log di layar.
 */
object ApiUiMessages {

    /** Pesan sukses bila server tidak mengembalikan message. */
    const val DEFAULT_SUCCESS = "Terkirim"

    /** Snackbar, detail log, dan teks status — tanpa kata "Barcode" dari respons API. */
    fun normalizeApiMessage(message: String?): String {
        val m = message?.trim() ?: return DEFAULT_SUCCESS
        if (m.isEmpty()) return DEFAULT_SUCCESS
        var out = m
        // Respons umum backend
        if (out.equals("Barcode scan received", ignoreCase = true)) {
            return "Scan diterima"
        }
        out = out.replace(Regex("(?i)barcode\\s+scan"), "Scan")
        out = out.replace(Regex("(?i)\\bbarcode\\b"), "scan")
        return out.trim()
    }
}
