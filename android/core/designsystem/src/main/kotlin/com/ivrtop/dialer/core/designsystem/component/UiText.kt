package com.ivrtop.dialer.core.designsystem.component

import androidx.annotation.StringRes
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.CallEndReason
import com.ivrtop.dialer.core.models.CallPhase

/** Friendly Hebrew/English message for every unified error code. */
@StringRes
fun errorMessageRes(code: AppErrorCode): Int = when (code) {
    AppErrorCode.NETWORK_UNAVAILABLE -> R.string.err_network
    AppErrorCode.DNS_FAILURE -> R.string.err_dns
    AppErrorCode.SERVER_UNREACHABLE -> R.string.err_unreachable
    AppErrorCode.TLS_HANDSHAKE_FAILED, AppErrorCode.TLS_CERT_INVALID -> R.string.err_tls
    AppErrorCode.TIMEOUT -> R.string.err_timeout
    AppErrorCode.AUTH_FAILED -> R.string.err_auth
    AppErrorCode.FORBIDDEN -> R.string.err_forbidden
    AppErrorCode.NOT_FOUND -> R.string.err_not_found
    AppErrorCode.BUSY -> R.string.end_busy
    AppErrorCode.DECLINED -> R.string.end_declined
    AppErrorCode.UNAVAILABLE -> R.string.end_unavailable
    AppErrorCode.CODEC_MISMATCH -> R.string.err_codec
    AppErrorCode.SERVER_ERROR -> R.string.err_server
    AppErrorCode.RATE_LIMITED -> R.string.err_rate_limited
    AppErrorCode.CANCELLED, AppErrorCode.REQUEST_TERMINATED -> R.string.end_cancelled
    AppErrorCode.INVALID_NUMBER -> R.string.err_invalid_number
    AppErrorCode.TRANSFER_FAILED -> R.string.err_transfer
    // Server-less links can't expire/be revoked; these codes are kept for contract compatibility.
    AppErrorCode.PROVISIONING_TOKEN_INVALID, AppErrorCode.PROVISIONING_TOKEN_EXPIRED,
    AppErrorCode.PROVISIONING_TOKEN_USED, AppErrorCode.PROVISIONING_TOKEN_REVOKED -> R.string.err_token_invalid
    AppErrorCode.PROVISIONING_SERVER_ERROR -> R.string.err_server
    AppErrorCode.ACCOUNT_DUPLICATE -> R.string.err_duplicate
    AppErrorCode.ACCOUNT_DISABLED_REMOTELY -> R.string.status_suspended
    AppErrorCode.LINK_DOMAIN_NOT_ALLOWED -> R.string.err_link_domain
    AppErrorCode.PERMISSION_MISSING -> R.string.err_permission
    AppErrorCode.ENGINE_NOT_READY -> R.string.err_engine
    AppErrorCode.EMERGENCY_NUMBER -> R.string.err_emergency
    AppErrorCode.NO_ACCOUNT -> R.string.err_no_account
    AppErrorCode.BLOCKED_NUMBER -> R.string.err_blocked
    AppErrorCode.UNKNOWN -> R.string.err_unknown
}

@StringRes
fun endReasonRes(reason: CallEndReason?): Int = when (reason) {
    CallEndReason.BUSY -> R.string.end_busy
    CallEndReason.DECLINED, CallEndReason.REJECTED_DND -> R.string.end_declined
    CallEndReason.NOT_FOUND -> R.string.err_not_found
    CallEndReason.UNAVAILABLE, CallEndReason.NO_ANSWER -> R.string.end_unavailable
    CallEndReason.NETWORK_LOST -> R.string.end_network_lost
    CallEndReason.SERVER_ERROR -> R.string.err_server
    CallEndReason.AUTH_FAILED -> R.string.err_auth
    CallEndReason.CANCELLED -> R.string.end_cancelled
    CallEndReason.ANSWERED_ELSEWHERE -> R.string.end_answered_elsewhere
    CallEndReason.MISSED -> R.string.end_missed
    CallEndReason.TRANSFERRED -> R.string.end_transferred
    CallEndReason.FAILED -> R.string.end_failed
    else -> R.string.end_ended
}

@StringRes
fun phaseRes(phase: CallPhase): Int = when (phase) {
    CallPhase.DIALING -> R.string.phase_dialing
    CallPhase.CONNECTING -> R.string.phase_connecting
    CallPhase.RINGING_REMOTE -> R.string.phase_ringing
    CallPhase.INCOMING_PENDING, CallPhase.RINGING_LOCAL -> R.string.phase_incoming
    CallPhase.ANSWERING -> R.string.phase_connecting
    CallPhase.ACTIVE -> R.string.phase_active
    CallPhase.HOLDING -> R.string.phase_holding
    CallPhase.REMOTE_HELD -> R.string.phase_remote_held
    CallPhase.TRANSFERRING -> R.string.phase_transferring
    CallPhase.ENDED -> R.string.end_ended
}
