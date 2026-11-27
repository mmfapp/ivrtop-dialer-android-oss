package com.ivrtop.dialer.telephony.telecom.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ivrtop.dialer.core.models.RegistrationState
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import com.ivrtop.dialer.telephony.telecom.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * "Persistent connection" mode only (user opt-in, for SIP servers without push).
 * Keeps the process alive with a visible, minimum-importance notification so PJSIP can keep
 * the REGISTER refreshed and receive INVITEs. No polling: PJSIP's own timers do the work.
 */
@AndroidEntryPoint
class KeepAliveService : LifecycleService() {
    @Inject lateinit var notifier: CallNotifier
    @Inject lateinit var registration: RegistrationManager

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            registration.states.collect { states ->
                val ok = states.values.count { it is RegistrationState.Registered }
                val text = if (ok > 0) getString(R.string.keepalive_ready) else getString(R.string.keepalive_connecting)
                promote(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        promote(getString(R.string.keepalive_connecting))
        return START_STICKY
    }

    private fun promote(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, CallNotifier.ID_KEEPALIVE, notifier.connectionNotification(text), type)
        } catch (e: Exception) {
            Timber.e(e, "keep-alive foreground failed")
            stopSelf()
        }
    }

    companion object {
        @Volatile private var running = false

        fun start(context: Context) {
            if (running) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
                running = true
            } catch (e: Exception) {
                Timber.w("KeepAliveService start refused: ${e.message}")
            }
        }

        fun stop(context: Context) {
            if (!running) return
            context.stopService(Intent(context, KeepAliveService::class.java))
            running = false
        }
    }
}
