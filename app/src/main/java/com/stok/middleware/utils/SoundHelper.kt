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
        /* intentional no-op */
    }
}
