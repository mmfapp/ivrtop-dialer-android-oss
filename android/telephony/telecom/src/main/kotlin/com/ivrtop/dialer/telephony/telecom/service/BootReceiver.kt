package com.ivrtop.dialer.telephony.telecom.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivrtop.dialer.core.common.ApplicationScope
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * After reboot / app update: in PERSISTENT mode (default) the registration manager starts the
 * KeepAliveService (specialUse FGS may be started from BOOT_COMPLETED, unlike phoneCall/microphone
 * on Android 15+), so incoming calls work again without opening the app.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var registration: RegistrationManager
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        scope.launch {
            try {
                registration.start()
            } finally {
                pending.finish()
            }
        }
    }
}
