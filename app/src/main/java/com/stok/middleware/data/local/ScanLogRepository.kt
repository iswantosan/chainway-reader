package com.stok.middleware.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stok.middleware.data.model.ScanLogItem
import java.util.concurrent.atomic.AtomicLong

/**
 * Penyimpanan log scan di local Android (SharedPreferences).
 */
class ScanLogRepository(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val typeToken = object : TypeToken<List<ScanLogItem>>() {}.type
    private val idGenerator = AtomicLong(System.currentTimeMillis())

    fun addLog(item: ScanLogItem): ScanLogItem {
        val withId = item.copy(id = idGenerator.incrementAndGet())
        val list = getLogs().toMutableList()
        list.add(0, withId)
        saveLogs(list)
        return withId
    }

    fun getLogs(): List<ScanLogItem> {
        val json = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        return try {
            gson.fromJson(json, typeToken) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun saveLogs(list: List<ScanLogItem>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_LOGS, json).apply()
    }

    companion object {
        private const val PREFS_NAME = "stok_scan_logs"
        private const val KEY_LOGS = "logs"
    }
}
