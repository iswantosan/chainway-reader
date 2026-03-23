package com.stok.middleware.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.stok.middleware.R
import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.data.local.ScanLogRepository
import com.stok.middleware.data.model.LogExportPayload
import com.stok.middleware.data.model.LogStatus
import com.stok.middleware.data.model.PendingScanRow
import com.stok.middleware.data.model.PendingScanState
import com.stok.middleware.data.model.ScanLogItem
import com.stok.middleware.data.model.ScanMode
import com.stok.middleware.databinding.ActivityMainBinding
import com.stok.middleware.scanner.BarcodeInputHandler
import com.stok.middleware.network.ApiConfig
import com.stok.middleware.network.ScanUpload
import com.stok.middleware.scanner.ScannerManager
import com.stok.middleware.utils.ScreenLog
import com.stok.middleware.utils.SoundHelper
import com.stok.middleware.ui.settings.SettingsActivity
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        /** Sama seperti [BarcodeInputHandler]: akhir burst scan tanpa Enter. */
        private const val WEDGE_IDLE_MS = 250L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var logRepository: ScanLogRepository
    private lateinit var scannerManager: ScannerManager
    private lateinit var barcodeHandler: BarcodeInputHandler
    private lateinit var logAdapter: ScanLogAdapter
    private lateinit var pendingAdapter: PendingScanAdapter

    private var lastScanValue: String = ""
    private val logListFlow = MutableStateFlow<List<ScanLogItem>>(emptyList())
    private val pendingScans = MutableStateFlow<List<PendingScanRow>>(emptyList())

    /** Buffer wedge saat fokus bukan di kolom scan (tombol/list mengambil fokus). */
    private val wedgeHandler = Handler(Looper.getMainLooper())
    private val wedgeBuffer = StringBuilder()
    private var wedgeFlushRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as com.stok.middleware.StokScannerApp
        prefs = app.appPreferences
        ScreenLog.sink = { tag, message -> addDebugLog(tag, message) }
        logRepository = ScanLogRepository(this)

        logAdapter = ScanLogAdapter(onCopyItem = { item ->
            copyToClipboard(ScanLogAdapter.formatLogLine(item))
        })
        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = logAdapter

        logListFlow.onEach { list ->
            logAdapter.submitList(list)
        }.launchIn(lifecycleScope)

        pendingAdapter = PendingScanAdapter()
        binding.recyclerPending.layoutManager = FullyExpandedLinearLayoutManager(this)
        binding.recyclerPending.adapter = pendingAdapter
        pendingScans.onEach { list ->
            pendingAdapter.submitList(list) {
                // Setelah diff, paksa ukuran ulang supaya NestedScrollView tahu tinggi konten penuh
                binding.recyclerPending.requestLayout()
            }
            val n = list.count { it.state == PendingScanState.PENDING }
            binding.textQueueCount.text = getString(R.string.queue_count, n)
        }.launchIn(lifecycleScope)

        loadLogs()

        scannerManager = ScannerManager(
            context = this,
            prefs = prefs,
            rfidCallback = { epc ->
                ScreenLog.d("[rfidCallback]", "ENTRY — epc length=${epc.length}, epc='${epc.take(50)}${if (epc.length > 50) "…" else ""}'")
                try {
                    runOnUiThread {
                        try {
                            ScreenLog.d("[rfidCallback]", "on UiThread — enqueue only (no API)")
                            addPendingScan(epc, ScanMode.RFID)
                        } catch (e: Exception) {
                            ScreenLog.e("[rfidCallback]", "EXCEPTION on UiThread", e)
                            throw e
                        }
                    }
                } catch (e: Exception) {
                    ScreenLog.e("[rfidCallback]", "EXCEPTION", e)
                }
            }
        )

        barcodeHandler = BarcodeInputHandler(
            editText = binding.editBarcode,
            onBarcodeScanned = { item ->
                addPendingScan(item.value, ScanMode.KEYBOARD)
            }
        )
        barcodeHandler.attach()
        binding.editBarcode.showSoftInputOnFocus = false

        binding.editBarcode.setOnLongClickListener {
            binding.editBarcode.showSoftInputOnFocus = true
            binding.editBarcode.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.editBarcode, InputMethodManager.SHOW_IMPLICIT)
            true
        }
        binding.editBarcode.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) (v as EditText).showSoftInputOnFocus = false
        }

        binding.btnClear.setOnClickListener { clearAll() }
        binding.btnSettings.setOnClickListener { showSettingsPasswordDialog() }
        binding.btnCopyLog.setOnClickListener { copyFullLog() }
        binding.btnSendLog.setOnClickListener { sendFullLog() }
        binding.btnCopyLastScan.setOnClickListener { copyLastScanValue() }
        binding.btnClearLastScan.setOnClickListener { clearLastScanValue() }
        binding.btnSendScans.setOnClickListener { sendPendingBatch() }
        binding.btnClearQueue.setOnClickListener { clearPendingQueue() }

        binding.textVersion.text = getString(R.string.app_version, getAppVersionName())

        binding.editBarcode.post { binding.editBarcode.requestFocus() }
        logRfidPermissionAndConfig()
        ScreenLog.d("[onCreate]", "Registering RFID receiver — action=${scannerManager.getRfidIntentAction()}, extraKey=${scannerManager.getRfidExtraKey()}")
        scannerManager.registerRfidReceiver()
    }

    override fun onResume() {
        super.onResume()
        ScreenLog.d("[onResume]", "Reloading RFID config")
        scannerManager.reloadRfidConfig()
        binding.editBarcode.post { binding.editBarcode.requestFocus() }
    }

    /**
     * Keyboard wedge mengirim key ke view yang fokus. Kalau fokus di tombol/list, kita tangkap di sini
     * (tanpa perlu keyboard layar / IME).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::binding.isInitialized) return super.dispatchKeyEvent(event)

        val et = binding.editBarcode
        if (et.hasFocus()) return super.dispatchKeyEvent(event)

        val focus = currentFocus
        if (focus is EditText && focus !== et) {
            return super.dispatchKeyEvent(event)
        }

        if (shouldPassKeyToSystemUnhandled(event)) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_UP) {
            if ((event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
                event.repeatCount == 0
            ) {
                cancelWedgeDebounce()
                flushWedgeBuffer()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (event.repeatCount > 0) return super.dispatchKeyEvent(event)

        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                cancelWedgeDebounce()
                flushWedgeBuffer()
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (wedgeBuffer.isNotEmpty()) {
                    wedgeBuffer.deleteCharAt(wedgeBuffer.length - 1)
                    scheduleWedgeDebounce()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            else -> {
                val ch = charFromKeyEvent(event) ?: return super.dispatchKeyEvent(event)
                wedgeBuffer.append(ch)
                scheduleWedgeDebounce()
                return true
            }
        }
    }

    private fun shouldPassKeyToSystemUnhandled(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_ALL_APPS,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_SEARCH -> true
            else -> false
        }
    }

    private fun charFromKeyEvent(event: KeyEvent): Char? {
        val uc = event.getUnicodeChar(event.metaState)
        if (uc != 0) {
            val c = uc.toChar()
            if (!c.isISOControl()) return c
            return null
        }
        return when (event.keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                '0' + (event.keyCode - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                var x = 'a' + (event.keyCode - KeyEvent.KEYCODE_A)
                if (event.isShiftPressed || event.isCapsLockOn) x = x.uppercaseChar()
                x
            }
            KeyEvent.KEYCODE_MINUS -> if (event.isShiftPressed) '_' else '-'
            KeyEvent.KEYCODE_PERIOD -> '.'
            KeyEvent.KEYCODE_COMMA -> ','
            KeyEvent.KEYCODE_SLASH -> '/'
            KeyEvent.KEYCODE_AT -> '@'
            KeyEvent.KEYCODE_SPACE -> ' '
            else -> null
        }
    }

    private fun scheduleWedgeDebounce() {
        cancelWedgeDebounce()
        wedgeFlushRunnable = Runnable {
            wedgeFlushRunnable = null
            flushWedgeBuffer()
        }
        wedgeHandler.postDelayed(wedgeFlushRunnable!!, WEDGE_IDLE_MS)
    }

    private fun cancelWedgeDebounce() {
        wedgeFlushRunnable?.let { wedgeHandler.removeCallbacks(it) }
        wedgeFlushRunnable = null
    }

    private fun flushWedgeBuffer() {
        val raw = wedgeBuffer.toString()
        wedgeBuffer.clear()
        val value = raw.trim()
        if (value.isEmpty()) return
        addPendingScan(value, ScanMode.KEYBOARD)
    }

    private fun clearWedgeBuffer() {
        cancelWedgeDebounce()
        wedgeBuffer.clear()
    }

    override fun onDestroy() {
        ScreenLog.d("[onDestroy]", "Unregistering RFID receiver")
        scannerManager.unregisterRfidReceiver()
        ScreenLog.sink = null
        super.onDestroy()
    }

    private fun addLogAndRefresh(item: ScanLogItem) {
        lifecycleScope.launch {
            logRepository.addLog(item)
            loadLogs()
            binding.recyclerLog.post { binding.recyclerLog.smoothScrollToPosition(0) }
        }
    }

    private fun addDebugLog(tag: String, message: String) {
        runOnUiThread {
            val item = ScanLogItem(
                id = 0L,
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                mode = ScanMode.RFID,
                value = tag,
                status = LogStatus.LOCAL_ONLY,
                detail = message
            )
            addLogAndRefresh(item)
        }
    }

    private fun loadLogs() {
        logListFlow.value = logRepository.getLogs()
    }

    private fun addPendingScan(rawValue: String, mode: ScanMode) {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val row = PendingScanRow(
            createdAt = ts,
            value = trimmed,
            mode = mode,
            state = PendingScanState.PENDING
        )
        pendingScans.update { listOf(row) + it }
        lastScanValue = trimmed
        binding.textLastScan.text = getString(R.string.scan_last, trimmed)
        if (mode == ScanMode.KEYBOARD) {
            binding.editBarcode.setText("")
        } else {
            binding.editBarcode.setText(trimmed)
        }
        binding.recyclerPending.post { binding.recyclerPending.smoothScrollToPosition(0) }
    }

    private fun updatePendingRow(localId: String, transform: (PendingScanRow) -> PendingScanRow) {
        pendingScans.update { list -> list.map { if (it.localId == localId) transform(it) else it } }
    }

    private fun clearPendingQueue() {
        pendingScans.value = emptyList()
    }

    private fun sendPendingBatch() {
        val toSend = pendingScans.value.filter { it.state == PendingScanState.PENDING }
        if (toSend.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.nothing_to_send), Snackbar.LENGTH_SHORT).show()
            return
        }
        binding.btnSendScans.isEnabled = false
        setApiLoading(true)
        binding.textStatus.text = getString(R.string.status_loading_scan)
        lifecycleScope.launch {
            var ok = 0
            var fail = 0
            for (row in toSend) {
                updatePendingRow(row.localId) { it.copy(state = PendingScanState.SENDING) }
                val apiSource = when (row.mode) {
                    ScanMode.KEYBOARD -> "barcode"
                    ScanMode.RFID -> "rfid"
                }
                val result = ScanUpload.upload(prefs, row.value, apiSource)
                result.fold(
                    onSuccess = { msg ->
                        ok++
                        updatePendingRow(row.localId) {
                            it.copy(state = PendingScanState.SENT, serverMessage = msg)
                        }
                    },
                    onFailure = { e ->
                        fail++
                        updatePendingRow(row.localId) {
                            it.copy(state = PendingScanState.FAILED, serverMessage = e.message)
                        }
                    }
                )
            }
            runOnUiThread {
                binding.btnSendScans.isEnabled = true
                setApiLoading(false)
                binding.textStatus.text = "-"
                Snackbar.make(
                    binding.root,
                    getString(R.string.batch_send_done, ok, fail),
                    Snackbar.LENGTH_LONG
                ).show()
                if (fail > 0) SoundHelper.playError()
            }
        }
    }

    private fun clearAll() {
        binding.editBarcode.setText("")
        clearLastScanValue()
        binding.textStatus.text = "-"
        logRepository.clearLogs()
        loadLogs()
        barcodeHandler.clear()
        clearWedgeBuffer()
        clearPendingQueue()
    }

    private fun copyLastScanValue() {
        if (lastScanValue.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.scan_empty), Snackbar.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(lastScanValue)
    }

    private fun clearLastScanValue() {
        lastScanValue = ""
        binding.textLastScan.text = getString(R.string.scan_last_empty)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Scan log", text))
        Snackbar.make(binding.root, getString(R.string.log_copied), Snackbar.LENGTH_SHORT).show()
    }

    private fun getAppVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    private fun buildFullLogText(): String? {
        val logs = logRepository.getLogs()
        if (logs.isEmpty()) return null
        return logs.joinToString("\n") { ScanLogAdapter.formatLogLine(it) }
    }

    private fun copyFullLog() {
        val text = buildFullLogText()
        if (text == null) {
            Snackbar.make(binding.root, getString(R.string.log_empty), Snackbar.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(text)
    }

    private fun setApiLoading(show: Boolean) {
        binding.progressStatus.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) binding.textStatus.text = "-"
    }

    private fun sendFullLog() {
        val text = buildFullLogText()
        if (text == null) {
            Snackbar.make(binding.root, getString(R.string.log_empty), Snackbar.LENGTH_SHORT).show()
            return
        }
        setApiLoading(true)
        binding.textStatus.text = getString(R.string.status_loading_log)
        lifecycleScope.launch {
            try {
                val service = ApiConfig.createRfidApiService(prefs)
                val url = ApiConfig.getLogSaveUrl(prefs)
                val httpResp = service.sendLog(url, LogExportPayload(log = text))
                val parseResult = com.stok.middleware.network.ApiResponseParser.parseLogSendResponse(httpResp)
                runOnUiThread {
                    setApiLoading(false)
                    val parsed = parseResult.getOrElse { e ->
                        SoundHelper.playError()
                        val msg = e.message ?: "Error"
                        Snackbar.make(binding.root, getString(R.string.log_send_failed, msg), Snackbar.LENGTH_LONG).show()
                        addLogAndRefresh(scanLogEntryForSendLogFailure(msg))
                        return@runOnUiThread
                    }
                    if (parsed.success == true) {
                        Snackbar.make(binding.root, getString(R.string.log_sent), Snackbar.LENGTH_SHORT).show()
                    } else {
                        SoundHelper.playError()
                        val msg = parsed.message ?: "Unknown"
                        Snackbar.make(binding.root, getString(R.string.log_send_failed, msg), Snackbar.LENGTH_LONG).show()
                        addLogAndRefresh(scanLogEntryForSendLogFailure(msg))
                    }
                }
            } catch (e: Exception) {
                val detail = when (e) {
                    is HttpException -> {
                        val code = e.code()
                        val body = e.response()?.errorBody()?.string()?.take(500) ?: ""
                        "HTTP $code" + if (body.isNotBlank()) " — $body" else ""
                    }
                    else -> e.message ?: "Error"
                }
                runOnUiThread {
                    setApiLoading(false)
                    SoundHelper.playError()
                    Snackbar.make(binding.root, getString(R.string.log_send_failed, detail), Snackbar.LENGTH_LONG).show()
                    addLogAndRefresh(scanLogEntryForSendLogFailure(detail))
                }
            }
        }
    }

    private fun scanLogEntryForSendLogFailure(detail: String): ScanLogItem {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return ScanLogItem(
            id = 0L,
            timestamp = timestamp,
            mode = ScanMode.KEYBOARD,
            value = "Kirim log",
            status = LogStatus.FAILED,
            detail = detail
        )
    }

    private fun getSettingsPassword(): String {
        return SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())
    }

    private fun showSettingsPasswordDialog() {
        val paddingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
        val input = EditText(this).apply {
            hint = getString(R.string.settings_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx, paddingPx, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_password_title)
            .setView(container)
            .setPositiveButton(R.string.settings_password_ok) { _, _ ->
                if (input.text.toString().trim() == getSettingsPassword()) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    Snackbar.make(binding.root, getString(R.string.settings_password_wrong), Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.settings_password_cancel, null)
            .setCancelable(true)
            .show()
    }

    private fun logRfidPermissionAndConfig() {
        try {
            val pm = packageManager
            val pkg = packageName
            val internet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.checkPermission(android.Manifest.permission.INTERNET, pkg) == PackageManager.PERMISSION_GRANTED
            } else {
                @Suppress("DEPRECATION")
                pm.checkPermission(android.Manifest.permission.INTERNET, pkg) == PackageManager.PERMISSION_GRANTED
            }
            val networkState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, pkg) == PackageManager.PERMISSION_GRANTED
            } else {
                @Suppress("DEPRECATION")
                pm.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, pkg) == PackageManager.PERMISSION_GRANTED
            }
            val msg = "INTERNET=$internet, ACCESS_NETWORK_STATE=$networkState (untuk API setelah scan)"
            ScreenLog.d("[permission]", msg)
        } catch (e: Exception) {
            ScreenLog.e("[permission]", "check EXCEPTION", e)
        }
    }
}
