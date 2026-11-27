package com.ivrtop.dialer.telephony.audio

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the screen off when the phone is held to the ear during an earpiece call
 * (PROXIMITY_SCREEN_OFF_WAKE_LOCK). Released for speaker / Bluetooth / headset routes.
 */
@Singleton
class ProximityController @Inject constructor(@ApplicationContext context: Context) {
    private val pm = context.getSystemService(PowerManager::class.java)
    private var lock: PowerManager.WakeLock? = null

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (lock?.isHeld == true) return
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            lock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ivrtop:proximity").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        } else {
            lock?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
            lock = null
        }
    }
}
