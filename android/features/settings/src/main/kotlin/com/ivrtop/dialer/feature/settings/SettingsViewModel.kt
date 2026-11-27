package com.ivrtop.dialer.feature.settings

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.common.BuildInfo
import com.ivrtop.dialer.core.common.log.Redactor
import com.ivrtop.dialer.core.common.log.RedactingTree
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.datastore.AppLanguage
import com.ivrtop.dialer.core.datastore.AppSettings
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.RegistrationState
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.network.NetworkMonitor
import com.ivrtop.dialer.core.network.NetworkState
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUi(
    val settings: AppSettings = AppSettings(),
    val accounts: List<SipAccount> = emptyList(),
    val registration: Map<String, RegistrationState> = emptyMap(),
    val network: NetworkState = NetworkState.NONE,
)

data class Diagnostics(
    val networkOk: Boolean,
    val registeredCount: Int,
    val accountCount: Int,
    val batteryUnrestricted: Boolean,
    val fullScreenAllowed: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
    accounts: AccountRepository,
    private val registration: RegistrationManager,
    network: NetworkMonitor,
    val buildInfo: BuildInfo,
) : ViewModel() {

    val state: StateFlow<SettingsUi> = combine(store.settings, accounts.accounts, registration.states, network.state) { s, a, r, n ->
        SettingsUi(s, a, r, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { store.update(t) }

    fun setLanguage(lang: AppLanguage) {
        update { it.copy(language = lang) }
        // Per-app language (Android 13+ native, AppCompat back-port below) – survives restarts.
        AppCompatDelegate.setApplicationLocales(
            if (lang.tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang.tag),
        )
    }

    fun reconnectAll() = viewModelScope.launch { registration.reconnectNow(null) }

    fun diagnostics(): Diagnostics {
        val s = state.value
        val pm = context.getSystemService(PowerManager::class.java)
        val fs = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        return Diagnostics(
            networkOk = s.network.isConnected,
            registeredCount = s.registration.values.count { it is RegistrationState.Registered },
            accountCount = s.accounts.count { it.enabled },
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName),
            fullScreenAllowed = fs,
        )
    }

    /** Builds a redacted technical report. Passwords/tokens never appear (Redactor + no secrets in models). */
    suspend fun technicalReport(): String {
        val s = state.value
        val d = diagnostics()
        val sb = StringBuilder()
        sb.appendLine("IVRTOP Dialer ${buildInfo.versionName} (${buildInfo.versionCode})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Mode: ${s.settings.connectivityMode}, wifiOnly=${s.settings.wifiOnly}")
        sb.appendLine("Network: ${s.network.type} validated=${s.network.validated} v6=${s.network.hasIpv6}")
        sb.appendLine("Diagnostics: $d")
        s.accounts.forEach { a ->
            sb.appendLine("Account ${a.id.take(8)}: ${Redactor.maskNumber(a.username)}@${a.domain} ${a.transport} srtp=${a.srtp} enabled=${a.enabled} state=${s.registration[a.id]?.let { it::class.simpleName }}")
        }
        sb.appendLine("---- log ----")
        RedactingTree.instance?.snapshot()?.forEach { sb.appendLine(it) }
        return Redactor.redact(sb.toString())
    }

    /** Wipes ALL app data (accounts, Keystore-encrypted secrets, history, settings) and closes the app. */
    fun deleteAllData() {
        context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
    }
}
