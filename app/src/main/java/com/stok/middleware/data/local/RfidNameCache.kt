package com.stok.middleware.data.local

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Cache nama produk per tag_id (dari GET /api/rfid/map), di RAM + JSON di [AppPreferences].
 */
object RfidNameCache {

    private val gson = Gson()

    @Volatile
    private var map: Map<String, String> = emptyMap()

    fun refreshFromPrefs(prefs: AppPreferences) {
        map = parseJson(prefs.rfidNameMapJson)
    }

    fun saveMap(prefs: AppPreferences, newMap: Map<String, String>) {
        prefs.rfidNameMapJson = gson.toJson(newMap)
        map = newMap
    }

    /** Nama produk jika ada di server; kalau tidak, kembalikan [tagId]. */
    fun nameForTag(tagId: String): String = map[tagId] ?: tagId

    private fun parseJson(json: String): Map<String, String> {
        return runCatching {
            val o = gson.fromJson(json, JsonObject::class.java) ?: return@runCatching emptyMap()
            buildMap {
                for (key in o.keySet()) {
                    val el = o.get(key) ?: continue
                    if (el.isJsonPrimitive) {
                        put(key, el.asString)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }
}
