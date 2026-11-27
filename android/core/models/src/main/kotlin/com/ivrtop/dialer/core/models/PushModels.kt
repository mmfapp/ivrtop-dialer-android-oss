package com.ivrtop.dialer.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Platform-neutral push message (FCM data payload on Android, PushKit payload on iOS).
 * Mirrors contracts/schemas/push-payload.schema.json.
 */
@Serializable
data class PushMessage(
    val type: PushType,
    /** Correlation id: equals the X-IVRTOP-Call-ID SIP header the PBX adds to the INVITE. */
    val callId: String? = null,
    val profileId: String? = null,
    val from: String? = null,
    val fromName: String? = null,
    /** Server send time (epoch ms) – stale pushes are discarded. */
    val sentAt: Long,
    /** Seconds after sentAt during which the call can still be answered. */
    val ttlSec: Int = 30,
    val reason: String? = null,
)

@Serializable
enum class PushType {
    @SerialName("incoming_call") INCOMING_CALL,
    @SerialName("cancel_call") CANCEL_CALL,
    @SerialName("answered_elsewhere") ANSWERED_ELSEWHERE,
    @SerialName("config_updated") CONFIG_UPDATED,
    @SerialName("account_disabled") ACCOUNT_DISABLED,
    @SerialName("device_revoked") DEVICE_REVOKED,
    @SerialName("mwi") MWI,
    @SerialName("ping") PING,
}
