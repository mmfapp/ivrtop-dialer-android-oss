package com.ivrtop.dialer.core.models

import kotlinx.serialization.Serializable

/**
 * Payload returned by POST /api/v1/provisioning/redeem.
 * Mirrors contracts/schemas/provisioning-redeem-response.schema.json.
 */
@Serializable
data class ProvisioningBundle(
    val profileId: String,
    val deviceId: String,
    /** Opaque bearer used for the devices and config endpoints. Stored in SecureCredentialStore. */
    val deviceToken: String,
    val configVersion: Int,
    val account: ProvisionedAccount,
    val policy: AccountPolicy = AccountPolicy(),
    val features: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class ProvisionedAccount(
    val displayName: String,
    val phoneNumber: String? = null,
    val username: String,
    val authUsername: String? = null,
    val password: String,
    val domain: String = SipAccount.DEFAULT_IVRTOP_DOMAIN,
    val registrar: String? = null,
    val outboundProxy: String? = null,
    val realm: String? = null,
    val port: Int? = null,
    val transport: SipTransport? = null,
    val srtp: SrtpMode = SrtpMode.OPTIONAL,
    val dtmfMethod: DtmfMethod = DtmfMethod.RFC4733,
    val registrationExpirySec: Int = 300,
    val stunServer: String? = null,
    val iceEnabled: Boolean = false,
    val pushEnabled: Boolean = true,
    val codecs: List<CodecPreference>? = null,
    val voicemailNumber: String? = null,
)

/** Steps shown during auto-detection (docs/AUTO_PROVISIONING.md §Auto-detect). */
enum class ProbeStep { LOCATE_SERVER, CHECK_CREDENTIALS, CHECK_CONNECTION, DONE }

data class ProbeCandidate(val transport: SipTransport, val host: String, val port: Int, val source: String)

sealed interface ProbeResult {
    data class Success(val candidate: ProbeCandidate, val registrationMs: Long) : ProbeResult
    data class Failure(val error: AppErrorCode, val technical: String, val tried: List<String>) : ProbeResult
}
