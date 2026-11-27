package com.ivrtop.dialer.core.models

import kotlinx.serialization.Serializable

@Serializable
enum class ContactSource { DEVICE, LOCAL, PBX, DIRECTORY }

@Serializable
data class ContactPhone(
    val number: String,
    val normalized: String?,
    /** Localised label from ContactsContract (e.g. "נייד", "עבודה"). */
    val label: String?,
    val isPrimary: Boolean = false,
)

@Serializable
data class Contact(
    val id: String,
    val source: ContactSource,
    val displayName: String,
    val phones: List<ContactPhone>,
    val photoUri: String? = null,
    val thumbnailUri: String? = null,
    val starred: Boolean = false,
    val lookupKey: String? = null,
    val sipAddresses: List<String> = emptyList(),
) {
    val initials: String
        get() = displayName.split(' ', '-').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "#" }
}

@Serializable
data class Favorite(
    val id: Long = 0,
    val contactId: String?,
    val displayName: String,
    val number: String,
    val photoUri: String? = null,
    val preferredAccountId: String? = null,
    val position: Int,
    /** Speed dial digit 2..9 or null */
    val speedDialDigit: Int? = null,
    /** Subscribe to BLF/presence for this extension when PBX supports it. */
    val blfEnabled: Boolean = false,
)

@Serializable
enum class PresenceState { UNKNOWN, AVAILABLE, BUSY, RINGING, OFFLINE, DND }
