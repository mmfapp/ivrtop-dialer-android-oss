package com.ivrtop.dialer.telephony.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-managed VoIP calls must alert the user themselves. Honours the ringer mode
 * (silent / vibrate / normal); system DND is enforced by the incoming-call notification channel.
 * A second call while in a call gets a short call-waiting beep instead of the ringtone.
 */
@Singleton
class RingtonePlayer @Inject constructor(@ApplicationContext private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var ringtone: Ringtone? = null
    private var vibrating = false
    private var waitingTone: android.media.ToneGenerator? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        }
    }

    @Synchronized
    fun start(customUri: String?, vibrate: Boolean, silent: Boolean) {
        stop()
        if (silent) return
        // Respect Android system Do-Not-Disturb: alert visually only (notification still shows).
        val zen = context.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter
        if (zen != android.app.NotificationManager.INTERRUPTION_FILTER_ALL &&
            zen != android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        ) return
        val mode = audioManager.ringerMode
        if (mode == AudioManager.RINGER_MODE_NORMAL) {
            val uri = customUri?.let(Uri::parse) ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ringtone = uri?.let { RingtoneManager.getRingtone(context, it) }?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                try { play() } catch (e: Exception) { Timber.w(e, "ringtone failed") }
            }
        }
        if (vibrate && mode != AudioManager.RINGER_MODE_SILENT) startVibration()
    }

    @Synchronized
    fun startCallWaiting() {
        stop()
        try {
            waitingTone = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60).also {
                it.startTone(android.media.ToneGenerator.TONE_SUP_CALL_WAITING, 3000)
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "call waiting tone unavailable")
        }
    }

    @Synchronized
    fun stop() {
        ringtone?.stop()
        ringtone = null
        waitingTone?.release()
        waitingTone = null
        if (vibrating) {
            vibrator.cancel()
            vibrating = false
        }
    }

    private fun startVibration() {
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 800, 600, 800, 1400)
        val effect = VibrationEffect.createWaveform(pattern, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
        }
        vibrating = true
    }
}
