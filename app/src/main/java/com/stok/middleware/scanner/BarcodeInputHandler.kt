package com.stok.middleware.scanner

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText

/**
 * Deteksi input scan dari keyboard wedge:
 * - Jika ada Enter/newline -> proses langsung.
 * - Jika tidak ada Enter, anggap scan selesai setelah teks tidak berubah 250ms (scanner yang tidak kirim Enter).
 * Setelah proses, field dikosongkan agar siap untuk scan berikutnya.
 */
class BarcodeInputHandler(
    private val editText: EditText,
    private val onScanCompleted: (String) -> Unit
) {

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
        onScanCompleted(value)
    }

    fun clear() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
        editText.setText("")
    }
}
