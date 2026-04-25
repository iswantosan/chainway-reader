package com.stok.middleware.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.data.local.RfidNameCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object RfidMapSync {

    private val gson = Gson()

    suspend fun sync(prefs: AppPreferences): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val base = prefs.baseUrl.trim().trimEnd('/')
            val url = "$base/api/rfid/map"
            val client = ApiConfig.createOkHttpClient(prefs)
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            val root = gson.fromJson(body, JsonObject::class.java) ?: error("JSON kosong")
            val dataEl = root.getAsJsonObject("data") ?: error("Field data tidak ada")
            val map = mutableMapOf<String, String>()
            for (e in dataEl.entrySet()) {
                if (e.value.isJsonPrimitive) {
                    map[e.key] = e.value.asString
                }
            }
            RfidNameCache.saveMap(prefs, map)
            map.size
        }
    }
}
