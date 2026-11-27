package com.ivrtop.dialer.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A SIP account as stored on the device. The password is NEVER part of this model –
 * it lives only in SecureCredentialStore (Android Keystore) keyed by [id].
 *
 * Mirrors contracts/schemas/sip-account.schema.json (shared with the future iOS app).
 */
@Serializable
data class SipAccount(
    val id: String,
    val displayName: String,
    val phoneNumber: String? = null,
    val username: String,
    val authUsername: String? = null,
    val domain: String = DEFAULT_IVRTOP_DOMAIN,
    val registrar: String? = null,
    val outboundProxy: String? = null,
    val realm: String? = null,
    val port: Int? = null,
    val transport: SipTransport = SipTransport.TLS,
    val registrationExpirySec: Int = 300,
    val keepAliveSec: Int = 25,
    val nat: NatSettings = NatSettings(),
    val codecs: List<CodecPreference> = CodecPreference.DEFAULTS,
    val dtmfMethod: DtmfMethod = DtmfMethod.RFC4733,
    val srtp: SrtpMode = SrtpMode.OPTIONAL,
    val pushEnabled: Boolean = false,
    val enabled: Boolean = true,
    val acceptIncoming: Boolean = true,
    val allowOutgoing: Boolean = true,
    val isDefault: Boolean = false,
    val ringtoneUri: String? = null,
    val dnd: Boolean = false,
    val colorArgb: Long = 0xFF0E78A6,
    val icon: AccountIcon = AccountIcon.PHONE,
    val voicemailNumber: String? = "*97",
    /** Set when the account came from IVRTOP provisioning; enables PBX-specific features. */
    val provisioning: ProvisioningInfo? = null,
    val policy: AccountPolicy = AccountPolicy(),
) {
    val effectiveAuthUser: String get() = authUsername?.takeIf { it.isNotBlank() } ?: username
    val sipUri: String get() = "sip:$username@$domain"
    val label: String get() = displayName.ifBlank { phoneNumber ?: username }

    companion object {
        const val DEFAULT_IVRTOP_DOMAIN = "sip.ivr.top"
    }
}

@Serializable
enum class SipTransport(val wire: String, val defaultPort: Int, val encrypted: Boolean) {
    @SerialName("udp") UDP("udp", 5060, false),
    @SerialName("tcp") TCP("tcp", 5060, false),
    @SerialName("tls") TLS("tls", 5061, true),
    @SerialName("ws") WS("ws", 80, false),
    @SerialName("wss") WSS("wss", 443, true),
}

@Serializable
enum class SrtpMode { @SerialName("disabled") DISABLED, @SerialName("optional") OPTIONAL, @SerialName("mandatory") MANDATORY }

@Serializable
enum class DtmfMethod { @SerialName("rfc4733") RFC4733, @SerialName("sip_info") SIP_INFO, @SerialName("inband") INBAND }

@Serializable
enum class AccountIcon { PHONE, OFFICE, HOME, HEADSET, STAR, BRIEFCASE }

@Serializable
data class NatSettings(
    val stunServer: String? = null,
    val turnServer: String? = null,
    val turnUsername: String? = null,
    /** TURN password is stored in SecureCredentialStore under "<accountId>:turn". */
    val turnEnabled: Boolean = false,
    val iceEnabled: Boolean = false,
    val rewriteContact: Boolean = true,
    val symmetricRtp: Boolean = true,
    val ipVersion: IpVersion = IpVersion.AUTO,
)

@Serializable
enum class IpVersion { @SerialName("auto") AUTO, @SerialName("ipv4") IPV4, @SerialName("ipv6") IPV6 }

@Serializable
data class CodecPreference(val id: String, val priority: Int, val enabled: Boolean = true) {
    companion object {
        /** Opus first (wideband, FEC), then G.722 HD, then G.711 for universal PSTN interop. */
        val DEFAULTS = listOf(
            CodecPreference("opus/48000", 250),
            CodecPreference("G722/16000", 200),
            CodecPreference("PCMA/8000", 150),
            CodecPreference("PCMU/8000", 140),
        )
    }
}

@Serializable
data class ProvisioningInfo(
    val profileId: String,
    val deviceId: String? = null,
    val serverBaseUrl: String,
    val managed: Boolean = true,
    val configVersion: Int = 1,
)

/** Central management policy (pushed from the IVRTOP server). */
@Serializable
data class AccountPolicy(
    val allowInsecureTransportFallback: Boolean = false,
    val allowUserEdit: Boolean = true,
    val allowPasswordReveal: Boolean = false,
    val requireSrtp: Boolean = false,
    val recordingIndicatorRequired: Boolean = true,
)
