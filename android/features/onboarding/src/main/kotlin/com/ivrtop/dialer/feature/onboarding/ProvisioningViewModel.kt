package com.ivrtop.dialer.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.data.ProvisioningRepository
import com.ivrtop.dialer.core.data.SetupLinkResult
import com.ivrtop.dialer.core.data.SetupRequest
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.ProbeResult
import com.ivrtop.dialer.core.models.ProbeStep
import com.ivrtop.dialer.telephony.sip.ConnectionTester
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SetupStep {
    data object Welcome : SetupStep
    data object Permissions : SetupStep
    data object Methods : SetupStep
    /** Shown before anything is saved – the user must confirm. Never displays the password. */
    data class Confirm(val request: SetupRequest, val updatesExisting: Boolean, val additional: Boolean) : SetupStep
    data class Testing(val current: ProbeStep) : SetupStep
    data class Done(val accountLabel: String) : SetupStep
    data class Failed(val error: AppErrorCode, val technical: String?, val canRetry: Boolean) : SetupStep
}

/**
 * Server-less setup (docs/AUTO_PROVISIONING.md):
 *   link / QR  →  parse locally  →  user confirms  →  one REGISTER test  →  save (Keystore)  →  dialer
 * A new user@domain is added as an additional account; an existing one is updated.
 * Nothing is persisted unless the registration test succeeds.
 */
@HiltViewModel
class ProvisioningViewModel @Inject constructor(
    private val provisioning: ProvisioningRepository,
    private val tester: ConnectionTester,
    private val registration: RegistrationManager,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _step = MutableStateFlow<SetupStep>(SetupStep.Welcome)
    val step: StateFlow<SetupStep> = _step.asStateFlow()
    private var pending: SetupStep.Confirm? = null

    fun start(fromDeepLink: String?, skipWelcome: Boolean) {
        if (fromDeepLink != null) submitLink(fromDeepLink) else if (skipWelcome) _step.value = SetupStep.Methods
    }

    fun toPermissions() { _step.value = SetupStep.Permissions }
    fun toMethods() { _step.value = SetupStep.Methods }

    fun submitLink(raw: String) {
        when (val r = provisioning.parseSetupUri(raw)) {
            is SetupLinkResult.Rejected -> _step.value = SetupStep.Failed(r.error, r.detail, canRetry = false)
            is SetupLinkResult.Ok -> viewModelScope.launch {
                SetupStep.Confirm(r.request, provisioning.isUpdate(r.request), provisioning.hasOtherAccounts(r.request))
                    .also { pending = it; _step.value = it }
            }
        }
    }

    fun confirm() {
        val c = pending ?: return
        val req = c.request
        _step.value = SetupStep.Testing(ProbeStep.LOCATE_SERVER)
        viewModelScope.launch {
            val base = provisioning.toAccount(req.account, id = "pending", existing = null, detectedTransport = null)
            val result = tester.detect(
                base = base,
                password = req.account.password,
                // An explicit transport in the link (e.g. transport=udp) is honoured as-is;
                // auto-detection never silently downgrades from TLS.
                allowInsecure = false,
                fixedTransport = req.account.transport,
            ) { s -> _step.value = SetupStep.Testing(s) }

            when (result) {
                is ProbeResult.Success -> try {
                    val saved = provisioning.install(req, result.candidate.transport)
                    settings.update { it.copy(onboardingCompleted = true, defaultAccountId = it.defaultAccountId ?: saved.id) }
                    pending = null
                    registration.reloadAccounts()
                    registration.reconnectNow(saved.id)
                    _step.value = SetupStep.Done(saved.label)
                } catch (e: AppException) {
                    _step.value = SetupStep.Failed(e.error, e.message, canRetry = false)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Saving/registering must never crash the app – show the reason instead.
                    _step.value = SetupStep.Failed(AppErrorCode.UNKNOWN, "${e.javaClass.simpleName}: ${e.message.orEmpty().take(300)}", canRetry = true)
                }
                is ProbeResult.Failure -> _step.value = SetupStep.Failed(
                    result.error,
                    result.technical + if (result.tried.isNotEmpty()) "\n" + result.tried.joinToString("\n") else "",
                    canRetry = result.error != AppErrorCode.AUTH_FAILED,
                )
            }
        }
    }

    fun retry() {
        val p = pending
        if (p != null) { _step.value = p; confirm() } else _step.value = SetupStep.Methods
    }

    fun backToMethods() {
        pending = null
        _step.value = SetupStep.Methods
    }

    override fun onCleared() {
        pending = null // drop the in-memory password
    }
}
