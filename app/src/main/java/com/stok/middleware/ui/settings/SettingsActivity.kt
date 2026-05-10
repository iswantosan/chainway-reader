package com.stok.middleware.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import com.stok.middleware.R
import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.data.local.ScanLogRepository
import com.stok.middleware.data.model.LogStatus
import com.stok.middleware.data.model.ScanLogItem
import com.stok.middleware.data.model.ScanMode
import com.stok.middleware.databinding.ActivitySettingsBinding
import com.stok.middleware.network.SheetsApi
import kotlinx.coroutines.launch
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

        binding.editSheetsUrl.setText(prefs.sheetsUrl)
        binding.editSheetsToken.setText(prefs.sheetsToken)
        binding.editRfidIntentAction.setText(prefs.rfidIntentAction)
        binding.editRfidExtraKey.setText(prefs.rfidExtraKey)
        binding.switchWedgeAsRfid.isChecked = prefs.wedgeAsRfid

        binding.btnSave.setOnClickListener { save() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
    }

    private fun testConnection() {
        val url = binding.editSheetsUrl.text.toString().trim()
        val token = binding.editSheetsToken.text.toString().trim()
        if (url.isBlank() || token.isBlank()) {
            Snackbar.make(binding.root, getString(R.string.test_connection_need_url_token), Snackbar.LENGTH_LONG).show()
            return
        }
        binding.btnTestConnection.isEnabled = false
        binding.progressConnection.visibility = android.view.View.VISIBLE
        binding.btnTestConnection.text = getString(R.string.test_connection_running)
        lifecycleScope.launch {
            val result = SheetsApi.ping(url, token)
            binding.btnTestConnection.isEnabled = true
            binding.progressConnection.visibility = android.view.View.GONE
            binding.btnTestConnection.text = getString(R.string.btn_test_connection)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            result.fold(
                onSuccess = {
                    logRepository.addLog(
                        ScanLogItem(
                            id = 0L,
                            timestamp = timestamp,
                            mode = ScanMode.KEYBOARD,
                            value = "Test Sheets",
                            status = LogStatus.LOCAL_ONLY,
                            detail = "OK"
                        )
                    )
                    Snackbar.make(binding.root, getString(R.string.test_connection_ok), Snackbar.LENGTH_LONG).show()
                },
                onFailure = { e ->
                    val msg = e.message ?: "Error"
                    logRepository.addLog(
                        ScanLogItem(
                            id = 0L,
                            timestamp = timestamp,
                            mode = ScanMode.KEYBOARD,
                            value = "Test Sheets",
                            status = LogStatus.FAILED,
                            detail = msg
                        )
                    )
                    Snackbar.make(binding.root, getString(R.string.test_connection_fail, msg), Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun save() {
        prefs.sheetsUrl = binding.editSheetsUrl.text.toString().trim()
        prefs.sheetsToken = binding.editSheetsToken.text.toString().trim()
        prefs.rfidIntentAction = binding.editRfidIntentAction.text.toString().trim().ifBlank {
            AppPreferences.DEFAULT_RFID_ACTION
        }
        prefs.rfidExtraKey = binding.editRfidExtraKey.text.toString().trim().ifBlank {
            AppPreferences.DEFAULT_RFID_EXTRA_KEY
        }
        prefs.wedgeAsRfid = binding.switchWedgeAsRfid.isChecked
        finish()
    }
}
