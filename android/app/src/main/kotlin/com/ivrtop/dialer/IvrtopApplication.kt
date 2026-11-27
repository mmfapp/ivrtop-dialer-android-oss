package com.ivrtop.dialer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.ivrtop.dialer.core.common.ApplicationScope
import com.ivrtop.dialer.core.common.log.RedactingTree
import com.ivrtop.dialer.telephony.sip.CallManager
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import com.ivrtop.dialer.telephony.telecom.service.CallNotifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class IvrtopApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: CallNotifier
    @Inject lateinit var registration: RegistrationManager
    // Eagerly create the call manager so it registers with Telecom and listens to engine events.
    @Inject lateinit var callManager: CallManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val tree = RedactingTree(logToLogcat = BuildConfig.DEBUG)
        RedactingTree.instance = tree
        Timber.plant(tree)

        notifier.createChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch { registration.onAppForeground(true) }
            }
            override fun onStop(owner: LifecycleOwner) {
                appScope.launch { registration.onAppForeground(false) }
            }
        })

        appScope.launch { registration.start() }
    }
}
