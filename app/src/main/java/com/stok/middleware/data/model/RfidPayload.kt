package com.stok.middleware.data.model

import com.google.gson.annotations.SerializedName

/**
 * Payload JSON untuk POST RFID scan ke API.
 * Format wajib: epc, scanTime (ISO datetime), deviceName, source.
 */
data class RfidPayload(
    @SerializedName("epc") val epc: String,
    @SerializedName("scanTime") val scanTime: String,
    @SerializedName("deviceName") val deviceName: String = "Chainway C72",
    @SerializedName("source") val source: String = "rfid"
)
