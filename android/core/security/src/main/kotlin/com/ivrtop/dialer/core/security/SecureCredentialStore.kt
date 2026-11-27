package com.ivrtop.dialer.core.security

/**
 * Stores secrets (SIP passwords, TURN passwords, device tokens) encrypted with a
 * non-exportable AES-256-GCM key held in Android Keystore (StrongBox when available).
 *
 * Keys used by the app:
 *   "sip:<accountId>"      SIP digest password
 *   "turn:<accountId>"     TURN password
 *   "device:<profileId>"   IVRTOP device bearer token
 */
interface SecureCredentialStore {
    suspend fun put(key: String, secret: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
    suspend fun removeByPrefix(prefix: String)
    suspend fun clearAll()

    companion object {
        fun sipKey(accountId: String) = "sip:$accountId"
        fun turnKey(accountId: String) = "turn:$accountId"
        fun deviceKey(profileId: String) = "device:$profileId"
    }
}
