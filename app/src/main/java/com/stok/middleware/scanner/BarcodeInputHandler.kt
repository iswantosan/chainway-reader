package com.stok.middleware.scanner

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import com.stok.middleware.data.model.ScanLogItem
import com.stok.middleware.data.model.ScanMode
import com.stok.middleware.data.model.LogStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Deteksi input scan dari keyboard wedge:
 * - Jika ada Enter/newline -> proses langsung.
 * - Jika tidak ada Enter, anggap scan selesai setelah teks tidak berubah 250ms (scanner yang tidak kirim Enter).
 * Setelah proses, field dikosongkan agar siap untuk scan berikutnya.
 */
class BarcodeInputHandler(
    private val editText: EditText,
    private val onBarcodeScanned: (ScanLogItem) -> Unit
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null

    companion object {
        private const val SCAN_DONE_DELAY_MS = 250L
    }

    fun attach() {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                if (text.contains('\n') || text.contains('\r')) {
                    pendingRunnable?.let { handler.removeCallbacks(it) }
                    pendingRunnable = null
                    val value = text.replace(Regex("[\r\n]+"), "").trim()
                    if (value.isNotEmpty()) {
                        processScan(value)
                    }
                    editText.setText("")
                    editText.setSelection(0)
                    return
                }
                pendingRunnable?.let { handler.removeCallbacks(it) }
                pendingRunnable = Runnable {
                    pendingRunnable = null
                    val value = editText.text.toString().trim()
                    if (value.isNotEmpty()) {
                        processScan(value)
                        editText.setText("")
                        editText.setSelection(0)
                    }
                }
                handler.postDelayed(pendingRunnable!!, SCAN_DONE_DELAY_MS)
            }
        })
        editText.setOnEditorActionListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event == null || event.action == KeyEvent.ACTION_UP) {
                    pendingRunnable?.let { handler.removeCallbacks(it) }
                    pendingRunnable = null
                    val value = editText.text.toString().trim()
                    if (value.isNotEmpty()) {
                        processScan(value)
                        editText.setText("")
                    }
                    true
                } else false
            } else false
        }
    }

    private fun processScan(value: String) {
        val item = ScanLogItem(
            id = 0L,
            timestamp = dateFormat.format(Date()),
            mode = ScanMode.KEYBOARD,
            value = value,
            status = LogStatus.LOCAL_ONLY
        )
        onBarcodeScanned(item)
    }

    fun clear() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
        editText.setText("")
    }
}
