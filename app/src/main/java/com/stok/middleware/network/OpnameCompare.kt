package com.stok.middleware.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.stok.middleware.data.local.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OpnameComparePayload(
    @SerializedName("scanned") val scanned: List<String>
)

data class OpnameCompareResult(
    val missingOnWeb: List<String>,
    val missingInScan: List<String>,
    val matched: List<String>,
    val message: String? = null
)

object OpnameCompareApi {
    private val gson = Gson()

    suspend fun compare(
        prefs: AppPreferences,
        scannedValues: List<String>
    ): Result<OpnameCompareResult> = withContext(Dispatchers.IO) {
        try {
            val uniqueScanned = scannedValues
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (uniqueScanned.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Data opname kosong"))
            }

            val service = ApiConfig.createRfidApiService(prefs)
            val url = ApiConfig.getOpnameCompareUrl(prefs)
            val httpResp = service.sendOpnameCompare(url, OpnameComparePayload(uniqueScanned))
            if (!httpResp.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("HTTP ${httpResp.code()}")
                )
            }

            val raw = try {
                httpResp.body()?.string().orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (raw.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Response compare kosong"))
            }
            val parsed = parseFlexibleCompare(raw, uniqueScanned)
                ?: return@withContext Result.failure(
                    IllegalStateException("Format response compare tidak dikenali")
                )
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFlexibleCompare(raw: String, scanned: List<String>): OpnameCompareResult? {
        val root = gson.fromJson(raw, JsonElement::class.java) ?: return null
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject

        val msg = obj.getStringOrNull("message") ?: obj.getStringOrNull("msg")
        val dataObj = if (obj.has("data") && obj.get("data").isJsonObject) obj.getAsJsonObject("data") else obj

        val missingOnWeb = firstList(dataObj, listOf("missing_on_web", "missingOnWeb", "not_found_on_web"))
        val missingInScan = firstList(dataObj, listOf("missing_in_scan", "missingInScan", "missing_scan"))
        val matched = firstList(dataObj, listOf("matched", "match", "found"))
        if (missingOnWeb != null || missingInScan != null || matched != null) {
            return OpnameCompareResult(
                missingOnWeb = missingOnWeb ?: emptyList(),
                missingInScan = missingInScan ?: emptyList(),
                matched = matched ?: emptyList(),
                message = msg
            )
        }

        // Fallback: server kirim daftar stok web saja -> compare di app
        val webList = firstList(dataObj, listOf("web_stock", "stock", "items", "data"))
        if (webList != null) {
            val scannedSet = scanned.toSet()
            val webSet = webList.toSet()
            return OpnameCompareResult(
                missingOnWeb = (scannedSet - webSet).sorted(),
                missingInScan = (webSet - scannedSet).sorted(),
                matched = (scannedSet intersect webSet).sorted(),
                message = msg
            )
        }
        return null
    }

    private fun firstList(obj: JsonObject, keys: List<String>): List<String>? {
        for (key in keys) {
            if (!obj.has(key)) continue
            val el = obj.get(key)
            val list = parseCodeList(el)
            if (list.isNotEmpty()) return list.distinct()
        }
        return null
    }

    private fun parseCodeList(el: JsonElement?): List<String> {
        if (el == null || el.isJsonNull) return emptyList()
        if (el.isJsonArray) return parseArray(el.asJsonArray)
        if (el.isJsonPrimitive) {
            val v = el.asString?.trim().orEmpty()
            return if (v.isEmpty()) emptyList() else listOf(v)
        }
        if (el.isJsonObject) {
            val obj = el.asJsonObject
            val nested = firstList(obj, listOf("items", "codes", "epcs"))
            if (nested != null) return nested
            return extractCodeFromObj(obj)?.let { listOf(it) } ?: emptyList()
        }
        return emptyList()
    }

    private fun parseArray(arr: JsonArray): List<String> {
        val out = mutableListOf<String>()
        for (item in arr) {
            when {
                item == null || item.isJsonNull -> Unit
                item.isJsonPrimitive -> {
                    val v = item.asString?.trim().orEmpty()
                    if (v.isNotEmpty()) out += v
                }
                item.isJsonObject -> {
                    extractCodeFromObj(item.asJsonObject)?.let { out += it }
                }
            }
        }
        return out
    }

    private fun extractCodeFromObj(obj: JsonObject): String? {
        val keys = listOf("epc", "code", "barcode", "sku", "rfid", "value")
        for (k in keys) {
            if (!obj.has(k)) continue
            val v = obj.get(k)?.asString?.trim().orEmpty()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun JsonObject.getStringOrNull(key: String): String? {
        if (!has(key)) return null
        val el = get(key)
        if (el == null || el.isJsonNull) return null
        return el.asString
    }
}

