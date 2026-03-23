package com.stok.middleware.network

import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.data.model.RfidPayload
import com.stok.middleware.utils.ApiUiMessages
import com.stok.middleware.utils.ScreenLog
import kotlinx.coroutines.Dispatchers
import retrofit2.HttpException
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Satu kali POST scan ke API (dipakai batch dari tombol "Kirim ke server").
 * @param apiSource "barcode" (keyboard/wedge) atau "rfid" (broadcast).
 */
object ScanUpload {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun upload(prefs: AppPreferences, value: String, apiSource: String): Result<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Kosong"))

        val scanTime = dateFormat.format(Date())
        val payload = RfidPayload(
            epc = trimmed,
            scanTime = scanTime,
            deviceName = "Chainway C72",
            source = apiSource
        )
        ScreenLog.d("[Upload]", "POST id=… value len=${trimmed.length} source=$apiSource")
        return withContext(Dispatchers.IO) {
            try {
                val service = ApiConfig.createRfidApiService(prefs)
                val url = ApiConfig.getRfidScanUrl(prefs)
                val httpResp = service.sendRfidScan(url, payload)
                val response = ApiResponseParser.parseRfidScanResponse(httpResp).getOrElse {
                    return@withContext Result.failure(it)
                }
                val ok = response.success != false
                val msg = ApiUiMessages.normalizeApiMessage(response.message)
                if (!ok) Result.failure(IllegalStateException(msg))
                else Result.success(msg)
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("Unable to resolve host") == true -> "Tidak ada koneksi internet"
                    e.message?.contains("timeout") == true -> "Timeout"
                    e is HttpException -> "HTTP ${e.code()}"
                    else -> e.message ?: "Error"
                }
                Result.failure(Exception(msg))
            }
        }
    }
}
