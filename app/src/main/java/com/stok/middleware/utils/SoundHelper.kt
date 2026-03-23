package com.stok.middleware.utils

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Suara feedback: **hanya gagal** (NACK). Sukses sengaja diam — beep scanner/hardware tetap dari perangkat.
 */
object SoundHelper {

    /** Tidak memutar apa pun (sukses tanpa bunyi). */
    fun playSuccess() { /* intentional no-op */ }

    fun playError() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 400)
            tg.release()
        } catch (_: Exception) { }
    }
}
