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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler RFID: validasi, debounce 2 detik untuk tag sama, POST ke API, callback status/log.
 */
class RfidScanHandler(
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
    private val lastEpcTime = ConcurrentHashMap<String, Long>()

    private fun log(tag: String, message: String) {
        ScreenLog.d(tag, message)
    }

    private fun logE(tag: String, message: String, e: Throwable? = null) {
        ScreenLog.e(tag, message, e)
    }

    companion object {
        private const val DEBOUNCE_MS = 2000L
    }

    fun onRfidScanned(epc: String) {
        log("[onRfidScanned]", "ENTRY — epc length=${epc.length}, epc='${epc.take(60)}${if (epc.length > 60) "…" else ""}'")
        val trimmed = try {
            epc.trim()
        } catch (e: Exception) {
            logE("[onRfidScanned]", "trim EXCEPTION", e)
            return
        }
        if (trimmed.isEmpty()) {
            log("[onRfidScanned]", "SKIP: trimmed empty")
            return
        }
        val now = System.currentTimeMillis()
        val last = lastEpcTime[trimmed] ?: 0L
        val elapsed = now - last
        log("[onRfidScanned]", "debounce check: last=$last, elapsed=${elapsed}ms, DEBOUNCE_MS=$DEBOUNCE_MS")
        if (elapsed < DEBOUNCE_MS) {
            log("[onRfidScanned]", "DEBOUNCE hit — treating as duplicate")
            onStatus("Duplicate (debounce)")
            onFailureMessage("Duplicate (debounce)")
            return
        }
        lastEpcTime[trimmed] = now
        log("[onRfidScanned]", "launching sendToApi in scope")
        scope.launch {
            try {
                sendToApi(trimmed)
            } catch (e: Exception) {
                logE("[onRfidScanned]", "sendToApi EXCEPTION in coroutine", e)
            }
        }
    }

    private suspend fun sendToApi(epc: String) {
        log("[sendToApi]", "ENTRY — epc='${epc.take(50)}…'")
        val scanTime = try {
            dateFormat.format(Date())
        } catch (e: Exception) {
            logE("[sendToApi]", "dateFormat EXCEPTION", e)
            return
        }
        val payload = RfidPayload(
            epc = epc,
            scanTime = scanTime,
            deviceName = "Chainway C72",
            source = "rfid"
        )
        log("[sendToApi]", "payload: epc=${payload.epc.take(30)}…, scanTime=$scanTime")
        onLoading(true)
        onStatus("Mengirim scan…")
        onLogItem(ScanLogItem(
            id = 0L,
            timestamp = scanTime,
            mode = ScanMode.RFID,
            value = epc,
            status = LogStatus.RECEIVED,
            detail = "Scan diterima, mengirim ke API"
        ))
        try {
            log("[sendToApi]", "Creating API service and URL from prefs")
            val service = ApiConfig.createRfidApiService(prefs)
            val url = ApiConfig.getRfidScanUrl(prefs)
            log("[sendToApi]", "URL='$url', calling sendRfidScan on IO")
            val httpResp = withContext(Dispatchers.IO) {
                try {
                    service.sendRfidScan(url, payload)
                } catch (e: Exception) {
                    logE("[sendToApi]", "sendRfidScan EXCEPTION (IO)", e)
                    throw e
                }
            }
            val parseResult = ApiResponseParser.parseRfidScanResponse(httpResp)
            val response = parseResult.getOrElse { e ->
                throw e
            }
            log("[sendToApi]", "response: success=${response.success}, message=${response.message}")
            val apiMessage = ApiUiMessages.normalizeApiMessage(response.message)
            val ok = response.success != false
            onLoading(false)
            if (!ok) {
                onStatus("Failed: $apiMessage")
                onFailureMessage(apiMessage)
                onLogItem(ScanLogItem(
                    id = 0L,
                    timestamp = dateFormat.format(Date()),
                    mode = ScanMode.RFID,
                    value = epc,
                    status = LogStatus.FAILED,
                    detail = apiMessage
                ))
                log("[sendToApi]", "API reported success=false — done")
                return
            }
            onStatus("OK")
            onSuccessMessage(apiMessage)
            onLogItem(ScanLogItem(
                id = 0L,
                timestamp = dateFormat.format(Date()),
                mode = ScanMode.RFID,
                value = epc,
                status = LogStatus.SENT,
                detail = apiMessage
            ))
            log("[sendToApi]", "SUCCESS — done")
        } catch (e: Exception) {
            val msg = when {
                e.message?.contains("Unable to resolve host") == true -> "Tidak ada koneksi internet"
                e.message?.contains("timeout") == true -> "Timeout"
                e is retrofit2.HttpException -> "HTTP ${e.code()}"
                e is com.google.gson.JsonIOException -> "Parse error"
                e.message?.contains("non-JSON") == true -> e.message ?: "Server bukan JSON"
                else -> (e.message ?: "Error")
            }
            logE("[sendToApi]", "FAILED: $msg", e)
            onLoading(false)
            onStatus("Failed: $msg")
            onFailureMessage(msg)
            onLogItem(ScanLogItem(
                id = 0L,
                timestamp = dateFormat.format(Date()),
                mode = ScanMode.RFID,
                value = epc,
                status = LogStatus.FAILED,
                detail = msg
            ))
        }
    }
}
