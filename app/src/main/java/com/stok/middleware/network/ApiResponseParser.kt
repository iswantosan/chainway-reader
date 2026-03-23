package com.stok.middleware.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.stok.middleware.data.model.RfidResponse
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Mem-parse body HTTP mentah ke [RfidResponse] tanpa Gson di Retrofit
 * (respons HTML / error page tidak memicu crash; Gson stream bisa melempar [com.google.gson.stream.MalformedJsonException]).
 */
object ApiResponseParser {

    private val gson = Gson()

    private fun readRawBody(httpResp: Response<ResponseBody>): String {
        return try {
            httpResp.body()?.string()
                ?: httpResp.errorBody()?.string()
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Respons tampak seperti halaman web, bukan JSON API. */
    fun looksLikeHtmlOrNonJson(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("<!DOCTYPE", ignoreCase = true) ||
            t.startsWith("<html", ignoreCase = true) ||
            t.startsWith("<!") && t.contains("<html", ignoreCase = true) ||
            (t.startsWith("<") && !t.startsWith("{"))
    }

    /**
     * Parse respons RFID/barcode scan atau kirim log (bentuk JSON sama: success/message).
     */
    fun parseRfidScanResponse(httpResp: Response<ResponseBody>): Result<RfidResponse> {
        return parseApiJsonResponse(httpResp)
    }

    fun parseLogSendResponse(httpResp: Response<ResponseBody>): Result<RfidResponse> {
        return parseApiJsonResponse(httpResp)
    }

    private fun parseApiJsonResponse(httpResp: Response<ResponseBody>): Result<RfidResponse> {
        val rawBody = readRawBody(httpResp)

        if (!httpResp.isSuccessful) {
            val snippet = rawBody.trim().take(400)
            val hint = if (looksLikeHtmlOrNonJson(rawBody)) {
                " (halaman HTML — periksa URL, token, dan endpoint API)"
            } else ""
            val msg = "HTTP ${httpResp.code()}$hint" + if (snippet.isNotEmpty()) " — $snippet" else ""
            return Result.failure(IllegalStateException(msg))
        }

        // Banyak backend PHP/Laravel mengembalikan 200 + body kosong → anggap sukses
        if (rawBody.isBlank()) {
            return Result.success(RfidResponse(success = true, message = "OK", data = null))
        }

        if (looksLikeHtmlOrNonJson(rawBody)) {
            return Result.failure(
                IllegalStateException(
                    "Server mengembalikan HTML, bukan JSON. Periksa Base URL, path endpoint, dan token Bearer. " +
                        "Pastikan backend mengembalikan JSON (bukan halaman login/SPA)."
                )
            )
        }

        val trimmed = rawBody.trim()

        // JSON object/array
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val fromFlexible = parseFlexibleJson(trimmed)
            if (fromFlexible != null) return Result.success(fromFlexible)
            val parsed = try {
                gson.fromJson(trimmed, RfidResponse::class.java)
            } catch (_: Exception) {
                null
            }
            if (parsed != null) return Result.success(parsed)
            return Result.failure(
                IllegalStateException("Bukan JSON valid: ${trimmed.take(300)}")
            )
        }

        // Teks polos: "OK", "success", angka, dll. (bukan HTML)
        if (trimmed.length <= 2000) {
            return Result.success(
                RfidResponse(success = true, message = trimmed, data = null)
            )
        }

        return Result.failure(IllegalStateException("Response tidak dikenali: ${trimmed.take(200)}…"))
    }

    /**
     * Terima variasi field dari backend: success, status, result, msg, message.
     */
    private fun parseFlexibleJson(json: String): RfidResponse? {
        return try {
            val o = gson.fromJson(json, JsonObject::class.java) ?: return null
            val success = when {
                o.has("success") && !o.get("success").isJsonNull -> o.get("success").asBoolean
                o.has("status") && o.get("status").isJsonPrimitive -> {
                    val s = o.get("status").asString.lowercase()
                    when {
                        s == "ok" || s == "success" || s == "200" || s == "true" -> true
                        s == "error" || s == "fail" || s == "failed" || s == "false" -> false
                        else -> true
                    }
                }
                o.has("result") && o.get("result").isJsonPrimitive -> {
                    val s = o.get("result").asString.lowercase()
                    s == "ok" || s == "success"
                }
                else -> true
            }
            val message = when {
                o.has("message") && !o.get("message").isJsonNull -> o.get("message").asString
                o.has("msg") && !o.get("msg").isJsonNull -> o.get("msg").asString
                o.has("error") && !o.get("error").isJsonNull -> o.get("error").asString
                else -> null
            }
            val dataEl = if (o.has("data")) o.get("data") else null
            RfidResponse(success = success, message = message, data = dataEl)
        } catch (_: Exception) {
            null
        }
    }
}
