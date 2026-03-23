package com.stok.middleware.utils

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Suara beep untuk scan sukses/gagal.
 */
object SoundHelper {

    fun playSuccess() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            tg.release()
        } catch (_: Exception) { }
    }

    fun playError() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 400)
            tg.release()
        } catch (_: Exception) { }
    }
}
