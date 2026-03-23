package com.stok.middleware.data.model

/**
 * Satu baris antrean scan sebelum / sesudah upload ke server.
 * [localId] UUID unik per kejadian scan (continuous scan = banyak baris).
 */
enum class PendingScanState {
    PENDING,
    SENDING,
    SENT,
    FAILED
}

data class PendingScanRow(
    val localId: String = java.util.UUID.randomUUID().toString(),
    val createdAt: String,
    val value: String,
    val mode: ScanMode,
    val stockOpMode: StockOpMode = StockOpMode.MASUK,
    val state: PendingScanState = PendingScanState.PENDING,
    val serverMessage: String? = null
) {
    /** 8 karakter pertama UUID untuk tampilan ringkas. */
    fun shortId(): String = localId.take(8)
}
