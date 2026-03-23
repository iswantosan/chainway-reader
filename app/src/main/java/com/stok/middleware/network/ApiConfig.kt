package com.stok.middleware.network

import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.utils.ScreenLog
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Konfigurasi Retrofit. Menggunakan AppPreferences untuk base URL dan endpoint.
 * Instance Retrofit harus bisa di-recreate saat config berubah (setelah Save di Settings).
 */
object ApiConfig {

    private const val CONNECT_TIMEOUT_SEC = 15L
    private const val READ_TIMEOUT_SEC = 15L
    private const val WRITE_TIMEOUT_SEC = 15L

    fun createOkHttpClient(prefs: AppPreferences): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(HttpScreenLogInterceptor())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                val token = prefs.staticToken
                if (token.isNotBlank()) {
                    request.addHeader("Authorization", "Bearer $token")
                    request.addHeader("X-Auth-Token", token)
                }
                chain.proceed(request.build())
            }
            .build()
    }

    fun createRetrofit(prefs: AppPreferences): Retrofit {
        val base = getRfidScanBaseUrl(prefs)
        return Retrofit.Builder()
            .baseUrl(base)
            .client(createOkHttpClient(prefs))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** URL lengkap untuk POST scan (RFID/barcode). Tanpa trailing slash agar tidak 405. */
    fun getRfidScanUrl(prefs: AppPreferences): String {
        var base = prefs.baseUrl.trim().trimEnd('/')
        var path = prefs.endpointPath.trim().trimStart('/')
        return if (path.isEmpty()) base else "$base/$path"
    }

    /** URL untuk POST kirim log (simpan ke file di server). */
    fun getLogSaveUrl(prefs: AppPreferences): String {
        val base = prefs.baseUrl.trim().trimEnd('/')
        return "$base/api/log/save"
    }

    fun getOpnameCompareUrl(prefs: AppPreferences): String {
        val base = prefs.baseUrl.trim().trimEnd('/')
        val path = prefs.opnameComparePath.trim().trimStart('/')
        return if (path.isEmpty()) "$base/api/opname/compare" else "$base/$path"
    }

    private fun getRfidScanBaseUrl(prefs: AppPreferences): String {
        var base = prefs.baseUrl.trim()
        if (!base.endsWith("/")) base += "/"
        return base
    }

    fun createRfidApiService(prefs: AppPreferences): RfidApiService {
        return createRetrofit(prefs).create(RfidApiService::class.java)
    }

    /**
     * Test koneksi API dengan GET ke [baseUrl]/api/ping.
     * @param baseUrl Base URL (dari form atau prefs)
     * @param token Token opsional untuk header
     * @return null jika sukses, atau pesan error jika gagal.
     */
    fun testConnection(baseUrl: String, token: String = ""): String? {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return "Base URL kosong"
        val pingUrl = "$base/api/ping"
        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(HttpScreenLogInterceptor())
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                if (token.isNotBlank()) {
                    req.addHeader("Authorization", "Bearer $token")
                    req.addHeader("X-Auth-Token", token)
                }
                chain.proceed(req.build())
            }
            .build()
        val request = Request.Builder().url(pingUrl).get().build()
        ScreenLog.d("[API/ping]", "GET $pingUrl")
        return try {
            val response = client.newCall(request).execute()
            val err = if (response.isSuccessful) null else "HTTP ${response.code}"
            if (err == null) ScreenLog.d("[API/ping]", "OK ${response.code}")
            else ScreenLog.w("[API/ping]", err)
            err
        } catch (e: Exception) {
            ScreenLog.e("[API/ping]", "Gagal", e)
            when {
                e.message?.contains("Unable to resolve host") == true -> "Tidak ada koneksi internet"
                e.message?.contains("timeout") == true -> "Timeout"
                e.message?.contains("Connection refused") == true -> "Koneksi ditolak"
                else -> e.message ?: "Error"
            }
        }
    }
}
