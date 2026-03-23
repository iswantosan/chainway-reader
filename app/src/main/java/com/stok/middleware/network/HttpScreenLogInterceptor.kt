package com.stok.middleware.network

import com.stok.middleware.utils.ScreenLog
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Mencatat request/response HTTP ke [ScreenLog] (tampil di layar).
 * Body request tidak dibaca ulang (menghindari konsumsi RequestBody).
 */
class HttpScreenLogInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        ScreenLog.d("[HTTP→]", "${req.method} ${req.url}")
        val response = chain.proceed(req)
        val peek = try {
            response.peekBody(MAX_PEEK_BYTES)
        } catch (_: Exception) {
            null
        }
        val snippet = try {
            peek?.string()?.take(MAX_BODY_CHARS) ?: ""
        } catch (_: Exception) {
            ""
        }
        val suffix = if (snippet.isNotEmpty()) " body=${snippet}${if ((peek?.contentLength() ?: 0) > MAX_BODY_CHARS) "…" else ""}" else ""
        ScreenLog.d("[HTTP←]", "${response.code} ${response.message}$suffix")
        return response
    }

    companion object {
        private const val MAX_PEEK_BYTES = 16_384L
        private const val MAX_BODY_CHARS = 2400
    }
}
