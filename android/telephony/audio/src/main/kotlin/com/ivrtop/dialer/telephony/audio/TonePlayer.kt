package com.ivrtop.dialer.telephony.audio

import android.media.AudioManager
import android.media.ToneGenerator
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Local feedback tones: keypad DTMF beeps and outgoing ringback (when the PBX sends no early media). */
@Singleton
class TonePlayer @Inject constructor() {
    private var keypad: ToneGenerator? = null
    private var ringback: ToneGenerator? = null

    @Synchronized
    fun playKey(key: Char, inCall: Boolean) {
        val tone = when (key) {
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (key - '0')
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        try {
            val gen = keypad ?: ToneGenerator(if (inCall) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_DTMF, 70).also { keypad = it }
            gen.startTone(tone, 120)
        } catch (e: RuntimeException) {
            Timber.d("keypad tone unavailable: ${e.message}")
        }
    }

    @Synchronized
    fun startRingback() {
        if (ringback != null) return
        try {
            ringback = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also { it.startTone(ToneGenerator.TONE_SUP_RINGTONE) }
        } catch (e: RuntimeException) {
            Timber.d("ringback unavailable: ${e.message}")
        }
    }

    @Synchronized
    fun stopRingback() {
        ringback?.stopTone()
        ringback?.release()
        ringback = null
    }

    @Synchronized
    fun playEndTone() {
        try {
            val gen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60)
            gen.startTone(ToneGenerator.TONE_PROP_PROMPT, 250)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ gen.release() }, 400)
        } catch (_: RuntimeException) { }
    }

    @Synchronized
    fun release() {
        keypad?.release(); keypad = null
        stopRingback()
    }
}
