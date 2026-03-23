package com.stok.middleware.data.model

import com.google.gson.annotations.SerializedName

/** Payload untuk POST kirim log ke API (simpan ke file). */
data class LogExportPayload(
    @SerializedName("log") val log: String
)
