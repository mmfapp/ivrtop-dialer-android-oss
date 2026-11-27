package com.ivrtop.dialer.telephony.telecom.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.models.CallPhase
import com.ivrtop.dialer.core.models.CallSnapshot
import com.ivrtop.dialer.telephony.sip.CallManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service (type phoneCall|microphone) alive while at least one call exists.
 * It renders the CallStyle notification for the foreground call and posts missed-call
 * notifications. Started by TelecomCallManager AFTER the call was added to Telecom.
 */
@AndroidEntryPoint
class CallService : LifecycleService() {

    @Inject lateinit var callManager: CallManager
    @Inject lateinit var notifier: CallNotifier
    @Inject lateinit var accounts: AccountRepository

    private var lastTypes = -1

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            callManager.calls.collect { list -> render(list) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Must call startForeground quickly – render synchronously with whatever we have.
        val current = callManager.foregroundCall.value
        if (current != null) promote(current, null) else stopSelf()
        return START_NOT_STICKY
    }

    private suspend fun render(list: List<CallSnapshot>) {
        // Missed-call notifications are posted by TelecomCallManager (works without this service).
        val live = list.filter { !it.phase.isTerminal }
        if (live.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val fg = live.firstOrNull { it.phase.isRinging } ?: live.firstOrNull { it.phase == CallPhase.ACTIVE } ?: live.first()
        promote(fg, accounts.get(fg.accountId)?.label)
    }

    private fun promote(call: CallSnapshot, accountLabel: String?) {
        val notification = notifier.callNotification(call, accountLabel)
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        // Microphone type only once the call is (being) connected; ringing needs phoneCall only.
        if (micGranted && !call.phase.isRinging) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            if (types != lastTypes) {
                ServiceCompat.startForeground(this, CallNotifier.ID_CALL, notification, types)
                lastTypes = types
            } else if (notifier.canPost()) {
                @Suppress("MissingPermission")
                NotificationManagerCompat.from(this).notify(CallNotifier.ID_CALL, notification)
            }
        } catch (e: Exception) {
            Timber.e(e, "startForeground failed")
            if (notifier.canPost()) {
                @Suppress("MissingPermission")
                NotificationManagerCompat.from(this).notify(CallNotifier.ID_CALL, notification)
            }
        }
    }

    companion object {
        /** @return false when Android refused the start (caller must post the notification itself). */
        fun start(context: Context): Boolean {
            val i = Intent(context, CallService::class.java)
            return try {
                ContextCompat.startForegroundService(context, i)
                true
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Timber.w("FGS start not allowed from background; posting the call notification directly")
                } else {
                    Timber.e(e, "CallService start failed")
                }
                false
            }
        }
    }
}
