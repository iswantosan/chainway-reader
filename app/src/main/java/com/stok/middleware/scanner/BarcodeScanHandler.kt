package com.stok.middleware.scanner

import com.stok.middleware.data.model.RfidPayload
import com.stok.middleware.data.model.ScanLogItem
import com.stok.middleware.data.model.ScanMode
import com.stok.middleware.data.model.LogStatus
import com.stok.middleware.network.ApiConfig
import com.stok.middleware.network.ApiResponseParser
import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.utils.ApiUiMessages
import com.stok.middleware.utils.ScreenLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * POST nilai scan dari keyboard/wedge ke API (endpoint sama dengan RFID, source=barcode di payload).
 */
class BarcodeScanHandler(
    private val prefs: AppPreferences,
    private val onLogItem: (ScanLogItem) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onLoading: (Boolean) -> Unit = {},
    private val onSuccessMessage: (String) -> Unit,
    private val onFailureMessage: (String) -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun sendBarcode(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            postToApi(trimmed)
        }
    }

    private suspend fun postToApi(value: String) {
        ScreenLog.d("[Scan]", "POST ke API — value length=${value.length}")
        val scanTime = dateFormat.format(Date())
        val payload = RfidPayload(
            epc = value,
            scanTime = scanTime,
            deviceName = "Chainway C72",
            source = "barcode"
        )
        onLoading(true)
        onStatus("Mengirim scan…")
        onLogItem(ScanLogItem(
            id = 0L,
            timestamp = scanTime,
            mode = ScanMode.KEYBOARD,
            value = value,
            status = LogStatus.RECEIVED,
            detail = "Scan diterima, mengirim ke API"
        ))
        try {
            val service = ApiConfig.createRfidApiService(prefs)
            val url = ApiConfig.getRfidScanUrl(prefs)
            ScreenLog.d("[Scan]", "URL=$url")
            val httpResp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                service.sendRfidScan(url, payload)
            }
            val response = ApiResponseParser.parseRfidScanResponse(httpResp).getOrElse { throw it }
            val apiMessage = ApiUiMessages.normalizeApiMessage(response.message)
            ScreenLog.d("[Scan]", "OK — success=${response.success} message=$apiMessage")
            onLoading(false)
            onStatus("OK")
            onSuccessMessage(apiMessage)
            onLogItem(ScanLogItem(
                id = 0L,
                timestamp = dateFormat.format(Date()),
                mode = ScanMode.KEYBOARD,
                value = value,
                status = LogStatus.SENT,
                detail = apiMessage
            ))
        } catch (e: Exception) {
            ScreenLog.e("[Scan]", "Gagal kirim ke API", e)
            val msg = when {
                e.message?.contains("Unable to resolve host") == true -> "Tidak ada koneksi internet"
                e.message?.contains("timeout") == true -> "Timeout"
                e is retrofit2.HttpException -> "HTTP ${e.code()}"
                e is com.google.gson.JsonIOException -> "Parse error"
                else -> (e.message ?: "Error")
            }
            onLoading(false)
            onStatus("Gagal: $msg")
            onFailureMessage(msg)
            onLogItem(ScanLogItem(
                id = 0L,
                timestamp = dateFormat.format(Date()),
                mode = ScanMode.KEYBOARD,
                value = value,
                status = LogStatus.FAILED,
                detail = msg
            ))
        }
    }
}
