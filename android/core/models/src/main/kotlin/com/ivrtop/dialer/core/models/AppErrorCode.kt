package com.ivrtop.dialer.core.models

import kotlinx.serialization.Serializable

/**
 * Unified error codes shared by Android, iOS and the backend
 * (contracts/error-codes.json is the source of truth).
 */
@Serializable
enum class AppErrorCode(val code: String) {
    NETWORK_UNAVAILABLE("E_NET_001"),
    DNS_FAILURE("E_NET_002"),
    SERVER_UNREACHABLE("E_NET_003"),
    TLS_HANDSHAKE_FAILED("E_NET_004"),
    TLS_CERT_INVALID("E_NET_005"),
    TIMEOUT("E_NET_006"),

    AUTH_FAILED("E_SIP_001"),
    FORBIDDEN("E_SIP_002"),
    NOT_FOUND("E_SIP_003"),
    BUSY("E_SIP_004"),
    DECLINED("E_SIP_005"),
    UNAVAILABLE("E_SIP_006"),
    CODEC_MISMATCH("E_SIP_007"),
    SERVER_ERROR("E_SIP_008"),
    RATE_LIMITED("E_SIP_009"),
    CANCELLED("E_SIP_010"),
    REQUEST_TERMINATED("E_SIP_011"),
    INVALID_NUMBER("E_SIP_012"),
    TRANSFER_FAILED("E_SIP_013"),

    PROVISIONING_TOKEN_INVALID("E_PRV_001"),
    PROVISIONING_TOKEN_EXPIRED("E_PRV_002"),
    PROVISIONING_TOKEN_USED("E_PRV_003"),
    PROVISIONING_TOKEN_REVOKED("E_PRV_004"),
    PROVISIONING_SERVER_ERROR("E_PRV_005"),
    ACCOUNT_DUPLICATE("E_PRV_006"),
    ACCOUNT_DISABLED_REMOTELY("E_PRV_007"),
    LINK_DOMAIN_NOT_ALLOWED("E_PRV_008"),

    PERMISSION_MISSING("E_APP_001"),
    ENGINE_NOT_READY("E_APP_002"),
    EMERGENCY_NUMBER("E_APP_003"),
    NO_ACCOUNT("E_APP_004"),
    BLOCKED_NUMBER("E_APP_005"),
    UNKNOWN("E_APP_999"),
}

class AppException(val error: AppErrorCode, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.code, cause)
