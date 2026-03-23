package com.stok.middleware.scanner

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.stok.middleware.data.local.AppPreferences
import com.stok.middleware.utils.ScreenLog

/**
 * Mendaftarkan receiver broadcast RFID (keyboard/wedge lewat [BarcodeInputHandler] terpisah).
 */
class ScannerManager(
    private val context: Context,
    private val prefs: AppPreferences,
    private val rfidCallback: (String) -> Unit
) {

    private var rfidReceiver: RfidBroadcastReceiver? = null

    fun getRfidIntentAction(): String = prefs.rfidIntentAction
    fun getRfidExtraKey(): String = prefs.rfidExtraKey

    private fun log(tag: String, message: String) {
        ScreenLog.d(tag, message)
    }

    private fun logE(tag: String, message: String, e: Throwable? = null) {
        ScreenLog.e(tag, message, e)
    }

    fun registerRfidReceiver() {
        val action = getRfidIntentAction()
        val extraKey = getRfidExtraKey()
        // Terima broadcast dari: Settings, Chainway C72, dan keyboardemulator (Asset Infinity doc)
        val acceptedActions = setOf(
            action,
            CHAINWAY_RFID_ACTION,
            KEYBOARDEMULATOR_RFID_ACTION
        )
        val msg = "ENTRY — action='$action', extraKey='$extraKey', acceptedActions=$acceptedActions, alreadyRegistered=${rfidReceiver != null}"
        log("[registerRfidReceiver]", msg)
        if (rfidReceiver != null) {
            log("[registerRfidReceiver]", "SKIP: receiver already registered")
            return
        }
        try {
            rfidReceiver = RfidBroadcastReceiver(
                acceptedActions = acceptedActions,
                extraKey = extraKey,
                onScan = rfidCallback
            )
            val filter = IntentFilter().apply {
                acceptedActions.forEach { addAction(it) }
            }
            // RECEIVER_EXPORTED agar broadcast dari app scanner (bukan app ini) bisa diterima.
            val flags = if (Build.VERSION.SDK_INT >= 33) {
                Context.RECEIVER_EXPORTED
            } else {
                null
            }
            log("[registerRfidReceiver]", "SDK_INT=${Build.VERSION.SDK_INT}, flags=RECEIVER_EXPORTED (menerima broadcast dari app lain/scanner)")
            if (flags != null) {
                context.registerReceiver(rfidReceiver, filter, flags)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(rfidReceiver, filter)
            }
            log("[registerRfidReceiver]", "OK — receiver registered for actions: $acceptedActions")
        } catch (e: Exception) {
            logE("[registerRfidReceiver]", "EXCEPTION", e)
            rfidReceiver = null
        }
    }

    fun unregisterRfidReceiver() {
        log("[unregisterRfidReceiver]", "ENTRY — receiver=${rfidReceiver != null}")
        rfidReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
                log("[unregisterRfidReceiver]", "OK — unregistered")
            } catch (e: Exception) {
                logE("[unregisterRfidReceiver]", "EXCEPTION", e)
            }
            rfidReceiver = null
        }
    }

    fun reloadRfidConfig() {
        log("[reloadRfidConfig]", "ENTRY")
        unregisterRfidReceiver()
        registerRfidReceiver()
        log("[reloadRfidConfig]", "DONE")
    }

    companion object {
        /** Action broadcast RFID dari Chainway C72 (Keyboardemulator / scanner). */
        const val CHAINWAY_RFID_ACTION = "com.rscja.scanner.action.scanner.RFID"
        /**
         * Action dari keyboardemulator saat Process Mode = Broadcast Receiver.
         * Sesuai dokumentasi Asset Infinity / Chainway C72: Broadcast name = android.intent.action.scanner.RFID, Key = data
         */
        const val KEYBOARDEMULATOR_RFID_ACTION = "android.intent.action.scanner.RFID"
    }
}
