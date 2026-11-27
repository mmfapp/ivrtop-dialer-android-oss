package com.ivrtop.dialer.telephony.telecom.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivrtop.dialer.core.common.ApplicationScope
import com.ivrtop.dialer.telephony.sip.CallManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Notification actions. Not exported: only our own PendingIntents can reach it. */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {
    @Inject lateinit var callManager: CallManager
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CallNotifier.EXTRA_CALL_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    CallNotifier.ACTION_DECLINE -> callManager.decline(callId)
                    CallNotifier.ACTION_HANGUP -> callManager.hangup(callId)
                    CallNotifier.ACTION_MUTE -> callManager.setMuted(callId, true)
                    CallNotifier.ACTION_UNMUTE -> callManager.setMuted(callId, false)
                    CallNotifier.ACTION_SILENCE -> callManager.silenceRinging(callId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
