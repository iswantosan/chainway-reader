package com.stok.middleware.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.data.local.ScanLogRepository
import com.stok.middleware.data.model.LogStatus
import com.stok.middleware.data.model.ScanLogItem
import com.stok.middleware.data.model.ScanMode
import com.stok.middleware.databinding.ActivitySettingsBinding
import com.stok.middleware.network.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPreferences
    private lateinit var logRepository: ScanLogRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as com.stok.middleware.StokScannerApp
        prefs = app.appPreferences
        logRepository = ScanLogRepository(this)

        binding.editBaseUrl.setText(prefs.baseUrl)
        binding.editEndpointPath.setText(prefs.endpointPath)
        binding.editStaticToken.setText(prefs.staticToken)
        binding.editRfidIntentAction.setText(prefs.rfidIntentAction)
        binding.editRfidExtraKey.setText(prefs.rfidExtraKey)

        binding.btnSave.setOnClickListener { save() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
    }

    private fun testConnection() {
        binding.btnTestConnection.isEnabled = false
        binding.progressConnection.visibility = android.view.View.VISIBLE
        binding.btnTestConnection.text = getString(com.stok.middleware.R.string.test_connection_running)
        Snackbar.make(binding.root, getString(com.stok.middleware.R.string.test_connection_running), Snackbar.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val baseUrl = binding.editBaseUrl.text.toString().trim()
            val token = binding.editStaticToken.text.toString().trim()
            val error = withContext(Dispatchers.IO) {
                ApiConfig.testConnection(baseUrl, token)
            }
            binding.btnTestConnection.isEnabled = true
            binding.progressConnection.visibility = android.view.View.GONE
            binding.btnTestConnection.text = getString(com.stok.middleware.R.string.btn_test_connection)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val detail = if (error == null) "Koneksi berhasil" else "Gagal: $error"
            logRepository.addLog(
                ScanLogItem(
                    id = 0L,
                    timestamp = timestamp,
                    mode = ScanMode.KEYBOARD,
                    value = "Test API",
                    status = LogStatus.LOCAL_ONLY,
                    detail = detail
                )
            )
            if (error == null) {
                Snackbar.make(binding.root, getString(com.stok.middleware.R.string.test_connection_ok), Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(binding.root, getString(com.stok.middleware.R.string.test_connection_fail, error), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun save() {
        prefs.baseUrl = binding.editBaseUrl.text.toString().trim()
        prefs.endpointPath = binding.editEndpointPath.text.toString().trim()
        prefs.staticToken = binding.editStaticToken.text.toString().trim()
        prefs.rfidIntentAction = binding.editRfidIntentAction.text.toString().trim().ifBlank {
            AppPreferences.DEFAULT_RFID_ACTION
        }
        prefs.rfidExtraKey = binding.editRfidExtraKey.text.toString().trim().ifBlank {
            AppPreferences.DEFAULT_RFID_EXTRA_KEY
        }
        finish()
    }
}
