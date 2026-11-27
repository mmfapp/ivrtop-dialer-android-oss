package com.ivrtop.dialer.core.models

import kotlinx.serialization.Serializable

/** User-facing connection status (see docs/UI_UX_SPECIFICATION.md §Header). */
@Serializable
sealed interface RegistrationState {
    @Serializable data object Disabled : RegistrationState
    @Serializable data object Idle : RegistrationState
    @Serializable data object Registering : RegistrationState
    @Serializable data class Registered(val expiresInSec: Int, val transport: SipTransport) : RegistrationState
    @Serializable data object NoNetwork : RegistrationState
    @Serializable data class Disconnected(val retryInSec: Int?) : RegistrationState
    @Serializable data object AuthFailed : RegistrationState
    @Serializable data class ServerUnreachable(val detail: String?) : RegistrationState
    @Serializable data object AccountSuspended : RegistrationState
    @Serializable data object CallsDisabled : RegistrationState
    @Serializable data class Error(val code: AppErrorCode, val sipStatus: Int? = null, val detail: String? = null) : RegistrationState

    val isUsable: Boolean get() = this is Registered
}

@Serializable
data class AccountStatus(val accountId: String, val state: RegistrationState)
