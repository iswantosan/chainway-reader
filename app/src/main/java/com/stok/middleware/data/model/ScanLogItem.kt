package com.stok.middleware.data.model

/**
 * Item log untuk RecyclerView.
 * Setiap item: timestamp, mode (KEYBOARD= wedge / RFID= broadcast), value, status.
 */
data class ScanLogItem(
    val id: Long,
    val timestamp: String,
    val mode: ScanMode,
    val value: String,
    val status: LogStatus,
    val detail: String? = null
)

enum class ScanMode {
    /** Input dari keyboard / wedge (bukan broadcast RFID). */
    KEYBOARD,
    RFID
}

enum class LogStatus {
    RECEIVED,
    SENT,
    FAILED,
    LOCAL_ONLY
}
