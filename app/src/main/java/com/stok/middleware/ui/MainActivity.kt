package com.stok.middleware.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.stok.middleware.R
import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.data.local.ScanLogRepository
import com.stok.middleware.data.model.LogStatus
import com.stok.middleware.data.model.ScanLogItem
import com.stok.middleware.data.model.ScanMode
import com.stok.middleware.databinding.ActivityMainBinding
import com.stok.middleware.network.SheetsApi
import com.stok.middleware.scanner.ScannerManager
import com.stok.middleware.ui.settings.SettingsActivity
import com.stok.middleware.utils.ScreenLog
import com.stok.middleware.utils.SoundHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        /** Akhir burst scan tanpa Enter saat fokus tidak di EditText apapun. */
        private const val WEDGE_IDLE_MS = 250L

        /** Maks jumlah log yang disimpan di memori (dan di-persist). */
        private const val LOG_CAP = 500

        /** Delay debounce sebelum persist log ke disk. */
        private const val LOG_PERSIST_DEBOUNCE_MS = 2000L

        /** Throttle pemanggilan submitList ke pending recycler — hindari beban DiffUtil
         *  untuk scan kencang. UI update di-batch tiap interval ini. */
        private const val PENDING_RENDER_THROTTLE_MS = 80L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var logRepository: ScanLogRepository
    private lateinit var scannerManager: ScannerManager
    private lateinit var logAdapter: ScanLogAdapter
    private lateinit var pendingAdapter: PendingRowAdapter

    /** Order-preserving map RFID -> total scan dalam session. */
    private val pending = MutableStateFlow<LinkedHashMap<String, Int>>(LinkedHashMap())
    private var isLogVisible: Boolean = false
    private val logListFlow = MutableStateFlow<List<ScanLogItem>>(emptyList())

    /** Buffer wedge keyboard. */
    private val wedgeHandler = Handler(Looper.getMainLooper())
    private val wedgeBuffer = StringBuilder()
    private var wedgeFlushRunnable: Runnable? = null

    /** Debounce persist log ke disk supaya scan kencang tidak ke-block I/O. */
    private val persistHandler = Handler(Looper.getMainLooper())
    private var persistRunnable: Runnable? = null

    /** Throttle render pending list. */
    private val renderHandler = Handler(Looper.getMainLooper())
    private var renderScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as com.stok.middleware.StokScannerApp
        prefs = app.appPreferences
        ScreenLog.sink = { tag, message -> addDebugLog(tag, message) }
        logRepository = ScanLogRepository(this)

        pendingAdapter = PendingRowAdapter()
        binding.recyclerPending.layoutManager = LinearLayoutManager(this)
        binding.recyclerPending.adapter = pendingAdapter

        logAdapter = ScanLogAdapter(onCopyItem = { item ->
            copyToClipboard(ScanLogAdapter.formatLogLine(item))
        })
        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = logAdapter

        logListFlow.onEach { list -> logAdapter.submitList(list) }
            .launchIn(lifecycleScope)

        pending.onEach { scheduleRenderPending() }
            .launchIn(lifecycleScope)

        loadLogs()

        scannerManager = ScannerManager(
            context = this,
            prefs = prefs,
            rfidCallback = { epc ->
                // Sengaja TIDAK log per broadcast — itu yang bikin scan kencang ngerasa lambat
                // (setiap log entry = submitList + DiffUtil + persist debounce trigger).
                // Untuk debug, aktifkan dengan toggle log section + scan manual.
                runOnUiThread { handleScan(epc) }
            }
        )

        binding.btnSendBatch.setOnClickListener { sendBatch() }
        binding.btnClearPending.setOnClickListener { clearPending() }
        binding.btnCopyLog.setOnClickListener { copyFullLog() }

        ScreenLog.d("[onCreate]", "Registering RFID receiver — action=${scannerManager.getRfidIntentAction()}, extraKey=${scannerManager.getRfidExtraKey()}")
        scannerManager.registerRfidReceiver()

        if (prefs.sheetsUrl.isBlank() || prefs.sheetsToken.isBlank()) {
            Snackbar.make(binding.root, getString(R.string.sheets_url_not_set), Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        ScreenLog.d("[onResume]", "Reloading RFID config")
        scannerManager.reloadRfidConfig()
        hideAllSoftKeyboards()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_overflow_menu, menu)
        val toggle = menu.findItem(R.id.menu_toggle_log)
        toggle?.title = if (isLogVisible) {
            getString(R.string.menu_hide_log)
        } else {
            getString(R.string.menu_show_log)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_toggle_log -> {
                isLogVisible = !isLogVisible
                binding.sectionLog.visibility = if (isLogVisible) View.VISIBLE else View.GONE
                invalidateOptionsMenu()
                true
            }
            R.id.menu_clear -> {
                clearAll()
                true
            }
            R.id.menu_settings -> {
                showSettingsPasswordDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Capture wedge keyboard di Activity level (tidak ada EditText fokus).
     * Receiver RFID broadcast jalan paralel via [ScannerManager].
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::binding.isInitialized) return super.dispatchKeyEvent(event)

        // Kalau kebetulan ada EditText lain (mis. dialog), biarkan event lewat.
        val focus = currentFocus
        if (focus is EditText) {
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
        handleScan(value)
    }

    override fun onPause() {
        super.onPause()
        flushPersistNow()
    }

    override fun onDestroy() {
        ScreenLog.d("[onDestroy]", "Unregistering RFID receiver")
        scannerManager.unregisterRfidReceiver()
        ScreenLog.sink = null
        flushPersistNow()
        super.onDestroy()
    }

    /**
     * Setiap nilai scan masuk: split kalau gabungan, lalu tiap RFID di-increment di [pending].
     * Tidak menulis log per-scan supaya scan kencang tidak terbebani I/O — pending list di UI
     * sudah jadi feedback visual yang cukup.
     */
    private fun handleScan(rawValue: String) {
        val parts = splitCombinedScanValues(rawValue)
        if (parts.isEmpty()) return
        pending.update { current ->
            val next = LinkedHashMap(current)
            for (v in parts) {
                next[v] = (next[v] ?: 0) + 1
            }
            next
        }
    }

    /**
     * Render pending list di-throttle agar saat scan kencang DiffUtil tidak dipanggil per scan.
     * State [pending] tetap update sinkron — UI catch up dalam [PENDING_RENDER_THROTTLE_MS].
     */
    private fun scheduleRenderPending() {
        if (renderScheduled) return
        renderScheduled = true
        renderHandler.postDelayed({
            renderScheduled = false
            doRenderPending(pending.value)
        }, PENDING_RENDER_THROTTLE_MS)
    }

    private fun doRenderPending(map: LinkedHashMap<String, Int>) {
        val totalRfid = map.size
        val totalScan = map.values.sum()
        binding.textHeaderTagId.text = getString(R.string.header_tag_id, totalRfid)
        binding.textHeaderCount.text = getString(R.string.header_count, totalScan)
        val rows = map.entries.map { PendingRow(it.key, it.value) }
        pendingAdapter.submitList(rows)
        binding.textPendingEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerPending.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun sendBatch() {
        val snapshot = pending.value
        if (snapshot.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.batch_empty), Snackbar.LENGTH_SHORT).show()
            return
        }
        if (prefs.sheetsUrl.isBlank() || prefs.sheetsToken.isBlank()) {
            Snackbar.make(binding.root, getString(R.string.sheets_url_not_set), Snackbar.LENGTH_LONG).show()
            return
        }
        val items: List<Pair<String, Int>> = snapshot.entries.map { it.key to it.value }
        binding.btnSendBatch.isEnabled = false
        Snackbar.make(binding.root, getString(R.string.batch_send_running, items.size), Snackbar.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = SheetsApi.appendBatch(prefs, items)
            binding.btnSendBatch.isEnabled = true
            result.fold(
                onSuccess = { detail ->
                    addLogAndRefresh(makeLog("BATCH", LogStatus.SENT, detail))
                    // Hapus dari antrean hanya RFID yang ikut dikirim — kalau user scan baru saat
                    // request berlangsung, scan barunya tidak ikut hilang.
                    pending.update { current ->
                        val next = LinkedHashMap(current)
                        for (key in snapshot.keys) {
                            val sent = snapshot[key] ?: 0
                            val now = next[key] ?: 0
                            val remaining = now - sent
                            if (remaining <= 0) next.remove(key) else next[key] = remaining
                        }
                        next
                    }
                    val parts = detail.split(",").joinToString("  •  ") { it.trim() }
                    Snackbar.make(binding.root, parts, Snackbar.LENGTH_LONG).show()
                },
                onFailure = { e ->
                    val msg = e.message ?: "Error"
                    SoundHelper.playError()
                    addLogAndRefresh(makeLog("BATCH", LogStatus.FAILED, msg))
                    Snackbar.make(binding.root, getString(R.string.batch_send_fail, msg), Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun clearPending() {
        pending.value = LinkedHashMap()
    }

    private fun makeLog(value: String, status: LogStatus, detail: String?): ScanLogItem {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return ScanLogItem(
            id = 0L,
            timestamp = ts,
            mode = ScanMode.RFID,
            value = value,
            status = status,
            detail = detail
        )
    }

    /**
     * In-memory append + debounced disk persist. Tidak boleh blok scan kencang.
     * Cap [LOG_CAP] agar list tidak tumbuh tak terbatas.
     */
    private fun addLogAndRefresh(item: ScanLogItem) {
        val withId = item.copy(id = System.nanoTime())
        logListFlow.update { current ->
            val next = ArrayList<ScanLogItem>(minOf(current.size + 1, LOG_CAP))
            next.add(withId)
            for (i in 0 until current.size) {
                if (next.size >= LOG_CAP) break
                next.add(current[i])
            }
            next
        }
        if (isLogVisible) {
            binding.recyclerLog.post { binding.recyclerLog.smoothScrollToPosition(0) }
        }
        schedulePersistLog()
    }

    private fun addDebugLog(tag: String, message: String) {
        val item = ScanLogItem(
            id = 0L,
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            mode = ScanMode.KEYBOARD,
            value = tag,
            status = LogStatus.LOCAL_ONLY,
            detail = message
        )
        runOnUiThread { addLogAndRefresh(item) }
    }

    private fun loadLogs() {
        logListFlow.value = logRepository.getLogs()
    }

    private fun schedulePersistLog() {
        persistRunnable?.let { persistHandler.removeCallbacks(it) }
        persistRunnable = Runnable {
            persistRunnable = null
            val snapshot = logListFlow.value
            lifecycleScope.launch(Dispatchers.IO) {
                logRepository.saveAll(snapshot)
            }
        }
        persistHandler.postDelayed(persistRunnable!!, LOG_PERSIST_DEBOUNCE_MS)
    }

    private fun flushPersistNow() {
        persistRunnable?.let { persistHandler.removeCallbacks(it) }
        persistRunnable = null
        val snapshot = logListFlow.value
        // Sinkron pada thread saat ini agar selesai sebelum onPause/onDestroy beneran return.
        try {
            logRepository.saveAll(snapshot)
        } catch (_: Exception) {
        }
    }

    private fun splitCombinedScanValues(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        val byDelimiter = trimmed
            .split(Regex("[\\r\\n,;\\t ]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (byDelimiter.size > 1) return byDelimiter

        val compactHex = trimmed.uppercase(Locale.ROOT)
        val isHex = compactHex.matches(Regex("[0-9A-F]+"))
        if (isHex && compactHex.length >= 48 && compactHex.length % 24 == 0) {
            return compactHex.chunked(24)
        }

        val byPrefix = compactHex
            .split(Regex("(?=E[0-9A-F]{3})"))
            .map { it.trim() }
            .filter { it.length >= 12 }
        if (byPrefix.size > 1) return byPrefix

        return listOf(trimmed)
    }

    private fun clearAll() {
        clearPending()
        logRepository.clearLogs()
        loadLogs()
        cancelWedgeDebounce()
        wedgeBuffer.clear()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Scan log", text))
        Snackbar.make(binding.root, getString(R.string.log_copied), Snackbar.LENGTH_SHORT).show()
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

    private fun hideAllSoftKeyboards() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        binding.root.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
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
}
