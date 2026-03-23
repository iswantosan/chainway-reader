package com.stok.middleware.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response dari API RFID (struktur umum; sesuaikan dengan backend).
 */
data class RfidResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: Any? = null
)
