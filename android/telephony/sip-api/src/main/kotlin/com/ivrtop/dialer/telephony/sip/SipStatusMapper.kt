package com.ivrtop.dialer.telephony.sip

import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.CallEndReason

/**
 * Maps SIP responses to app semantics.
 * IMPORTANT: 401/407 are part of normal Digest authentication. They are only treated as
 * "wrong password" when they are the FINAL response of a REGISTER/INVITE transaction
 * (i.e. PJSIP already retried with credentials and was challenged again).
 */
object SipStatusMapper {

    fun registrationError(code: Int, transportError: Boolean): AppErrorCode = when {
        transportError -> AppErrorCode.SERVER_UNREACHABLE
        code == 401 || code == 407 -> AppErrorCode.AUTH_FAILED
        code == 403 -> AppErrorCode.FORBIDDEN
        code == 404 -> AppErrorCode.NOT_FOUND
        code == 408 -> AppErrorCode.TIMEOUT
        code == 429 -> AppErrorCode.RATE_LIMITED
        // PJSIP synthesises 408/502/503 locally for DNS, TCP/TLS connect and timeout failures.
        code == 502 || code == 503 || code == 504 -> AppErrorCode.SERVER_UNREACHABLE
        code in 500..599 -> AppErrorCode.SERVER_ERROR
        else -> AppErrorCode.UNKNOWN
    }

    fun endReason(code: Int, localHangup: Boolean, wasConnected: Boolean): CallEndReason = when {
        wasConnected -> if (localHangup) CallEndReason.LOCAL_HANGUP else CallEndReason.REMOTE_HANGUP
        localHangup -> CallEndReason.CANCELLED
        code == 486 || code == 600 -> CallEndReason.BUSY
        code == 603 -> CallEndReason.DECLINED
        code == 404 || code == 484 || code == 604 -> CallEndReason.NOT_FOUND
        code == 480 || code == 410 -> CallEndReason.UNAVAILABLE
        code == 408 -> CallEndReason.NO_ANSWER
        code == 487 -> CallEndReason.CANCELLED
        code == 401 || code == 407 || code == 403 -> CallEndReason.AUTH_FAILED
        code in 500..599 -> CallEndReason.SERVER_ERROR
        code == 200 -> CallEndReason.REMOTE_HANGUP
        else -> CallEndReason.FAILED
    }

    fun errorFor(code: Int): AppErrorCode = when (code) {
        401, 407 -> AppErrorCode.AUTH_FAILED
        403 -> AppErrorCode.FORBIDDEN
        404, 604 -> AppErrorCode.NOT_FOUND
        484 -> AppErrorCode.INVALID_NUMBER
        486, 600 -> AppErrorCode.BUSY
        603 -> AppErrorCode.DECLINED
        480, 410 -> AppErrorCode.UNAVAILABLE
        488, 606 -> AppErrorCode.CODEC_MISMATCH
        487 -> AppErrorCode.REQUEST_TERMINATED
        408 -> AppErrorCode.TIMEOUT
        429 -> AppErrorCode.RATE_LIMITED
        in 500..599 -> AppErrorCode.SERVER_ERROR
        else -> AppErrorCode.UNKNOWN
    }
}
