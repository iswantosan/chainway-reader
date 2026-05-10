package com.stok.middleware.network

import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.utils.ScreenLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Client untuk Apps Script Web App. Tiap scan -> 1 POST -> 1 baris di sheet.
 * Token dishare lewat body, bukan header (Apps Script mengupas semua header custom).
 */
object SheetsApi {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        // Apps Script doPost di-redirect dari /macros/s/.../exec ke googleusercontent.com.
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val INDONESIAN_LOCALE = Locale("in", "ID")

    /** Kolom A: tahun, mis. 2026. */
    fun formatYear(date: Date = Date()): Int {
        return SimpleDateFormat("yyyy", INDONESIAN_LOCALE).format(date).toInt()
    }

    /** Kolom B: bulan angka tanpa padding, mis. 5. */
    fun formatMonth(date: Date = Date()): Int {
        return SimpleDateFormat("M", INDONESIAN_LOCALE).format(date).toInt()
    }

    /** Kolom C: "09 Mei 2026". */
    fun formatDate(date: Date = Date()): String {
        return SimpleDateFormat("dd MMM yyyy", INDONESIAN_LOCALE).format(date)
    }

    /** Kolom D: "14.07". */
    fun formatTime(date: Date = Date()): String {
        return SimpleDateFormat("HH.mm", INDONESIAN_LOCALE).format(date)
    }

    suspend fun appendScan(prefs: AppPreferences, rfid: String, qty: Int = 1): Result<String> {
        val now = Date()
        val body = JSONObject()
            .put("token", prefs.sheetsToken)
            .put("action", "append")
            .put("year", formatYear(now))
            .put("month", formatMonth(now))
            .put("date", formatDate(now))
            .put("time", formatTime(now))
            .put("rfid", rfid)
            .put("qty", qty)
        return post(prefs, body, tag = "[SheetsApi.appendScan]")
            .map { json ->
                val row = json.optInt("scanRow", json.optInt("row", -1))
                val newRfid = json.optBoolean("newRfid", false)
                if (newRfid) "row=$row, RFID baru → daftar" else "row=$row"
            }
    }

    /**
     * Kirim banyak baris dalam 1 request. [items] adalah pasangan rfid -> qty (total scan).
     * Tanggal & jam dihitung saat fungsi dipanggil (waktu kirim, bukan waktu scan masing-masing).
     */
    suspend fun appendBatch(prefs: AppPreferences, items: List<Pair<String, Int>>): Result<String> {
        if (items.isEmpty()) return Result.failure(IllegalArgumentException("antrean kosong"))
        val now = Date()
        val year = formatYear(now)
        val month = formatMonth(now)
        val date = formatDate(now)
        val time = formatTime(now)
        val arr = JSONArray()
        for ((rfid, qty) in items) {
            arr.put(
                JSONObject()
                    .put("year", year)
                    .put("month", month)
                    .put("date", date)
                    .put("time", time)
                    .put("rfid", rfid)
                    .put("qty", qty)
            )
        }
        val body = JSONObject()
            .put("token", prefs.sheetsToken)
            .put("action", "appendBatch")
            .put("items", arr)
        return post(prefs, body, tag = "[SheetsApi.appendBatch]")
            .map { json ->
                val inserted = json.optInt("inserted", -1)
                val newRfids = json.optInt("newRfids", 0)
                "inserted=$inserted, RFID baru=$newRfids"
            }
    }

    suspend fun ping(url: String, token: String): Result<String> {
        val body = JSONObject()
            .put("token", token)
            .put("action", "ping")
        return postRaw(url, body, tag = "[SheetsApi.ping]")
            .map { json -> json.optString("action", "ok") }
    }

    private suspend fun post(prefs: AppPreferences, body: JSONObject, tag: String): Result<JSONObject> {
        val url = prefs.sheetsUrl
        if (url.isBlank()) return Result.failure(IllegalStateException("Sheets URL belum di-set"))
        if (prefs.sheetsToken.isBlank()) return Result.failure(IllegalStateException("Token belum di-set"))
        return postRaw(url, body, tag)
    }

    private suspend fun postRaw(url: String, body: JSONObject, tag: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON))
                .build()
            ScreenLog.d(tag, "POST $url body=${body.toString().take(200)}")
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = "HTTP ${resp.code} — ${raw.take(200)}"
                    ScreenLog.w(tag, msg)
                    return@withContext Result.failure(RuntimeException(msg))
                }
                val json = try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    val msg = "Respons bukan JSON: ${raw.take(200)}"
                    ScreenLog.w(tag, msg)
                    return@withContext Result.failure(RuntimeException(msg))
                }
                if (!json.optBoolean("ok", false)) {
                    val err = json.optString("error", "unknown error")
                    ScreenLog.w(tag, "ok=false error=$err")
                    return@withContext Result.failure(RuntimeException(err))
                }
                ScreenLog.d(tag, "OK ${raw.take(200)}")
                Result.success(json)
            }
        } catch (e: Exception) {
            ScreenLog.e(tag, "EXCEPTION", e)
            Result.failure(e)
        }
    }
}
