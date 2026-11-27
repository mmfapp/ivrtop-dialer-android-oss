package com.ivrtop.dialer.core.data

import com.ivrtop.dialer.core.common.Clock
import com.ivrtop.dialer.core.database.AccountDao
import com.ivrtop.dialer.core.database.AccountEntity
import com.ivrtop.dialer.core.database.CallLogDao
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.security.SecureCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accounts = Room row (non-secret JSON config) + Keystore-encrypted password.
 * `AccountManager` in the spec maps to this repository + RegistrationManager.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val callLogDao: CallLogDao,
    private val vault: SecureCredentialStore,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Emits the id of an account whose config changed, so the RegistrationManager reloads it. */
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changes: SharedFlow<String> = _changes

    val accounts: Flow<List<SipAccount>> = dao.observeAll().map { rows -> rows.mapNotNull(::decode) }

    suspend fun all(): List<SipAccount> = dao.getAll().mapNotNull(::decode)

    suspend fun get(id: String): SipAccount? = dao.get(id)?.let(::decode)

    suspend fun defaultAccount(): SipAccount? {
        val list = all().filter { it.enabled && it.allowOutgoing }
        val preferred = settings.current().defaultAccountId
        return list.firstOrNull { it.id == preferred } ?: list.firstOrNull { it.isDefault } ?: list.firstOrNull()
    }

    suspend fun findDuplicate(username: String, domain: String): SipAccount? =
        dao.findByIdentity(username.trim(), domain.trim().lowercase())?.let(::decode)

    suspend fun password(id: String): String? = vault.get(SecureCredentialStore.sipKey(id))
    suspend fun turnPassword(id: String): String? = vault.get(SecureCredentialStore.turnKey(id))

    /**
     * Inserts or updates. [password] == null keeps the stored password.
     * Throws ACCOUNT_DUPLICATE when another account already uses the same user@domain.
     */
    suspend fun save(account: SipAccount, password: String?, turnPassword: String? = null): SipAccount {
        val normalized = account.copy(
            id = account.id.ifBlank { UUID.randomUUID().toString() },
            username = account.username.trim(),
            domain = account.domain.trim().lowercase(),
        )
        dao.findByIdentity(normalized.username, normalized.domain)?.let { existing ->
            if (existing.id != normalized.id) throw AppException(AppErrorCode.ACCOUNT_DUPLICATE)
        }
        password?.let { vault.put(SecureCredentialStore.sipKey(normalized.id), it) }
        turnPassword?.let { vault.put(SecureCredentialStore.turnKey(normalized.id), it) }
        val sort = dao.get(normalized.id)?.sortOrder ?: dao.nextSortOrder()
        val isFirst = dao.getAll().none { it.id != normalized.id }
        val final = if (isFirst) normalized.copy(isDefault = true) else normalized
        if (final.isDefault) clearOtherDefaults(final.id)
        dao.upsert(
            AccountEntity(
                id = final.id, username = final.username, domain = final.domain, sortOrder = sort,
                configJson = json.encodeToString(SipAccount.serializer(), final), updatedAt = clock.now(),
            ),
        )
        _changes.tryEmit(final.id)
        return final
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = update(id) { it.copy(enabled = enabled) }
    suspend fun setDnd(id: String, dnd: Boolean) = update(id) { it.copy(dnd = dnd) }

    suspend fun setDefault(id: String) {
        clearOtherDefaults(id)
        update(id) { it.copy(isDefault = true) }
        settings.update { it.copy(defaultAccountId = id) }
    }

    suspend fun update(id: String, transform: (SipAccount) -> SipAccount) {
        val cur = get(id) ?: return
        save(transform(cur), password = null)
    }

    /** Removes account, its secrets and (optionally) its call history. */
    suspend fun delete(id: String, deleteHistory: Boolean) {
        dao.delete(id)
        vault.remove(SecureCredentialStore.sipKey(id))
        vault.remove(SecureCredentialStore.turnKey(id))
        if (deleteHistory) callLogDao.deleteForAccount(id)
        if (settings.current().defaultAccountId == id) settings.update { it.copy(defaultAccountId = null) }
        _changes.tryEmit(id)
    }

    private suspend fun clearOtherDefaults(id: String) {
        dao.getAll().mapNotNull(::decode).filter { it.isDefault && it.id != id }.forEach { other ->
            val updated = other.copy(isDefault = false)
            dao.upsert(
                AccountEntity(other.id, other.username, other.domain, dao.get(other.id)?.sortOrder ?: 0,
                    json.encodeToString(SipAccount.serializer(), updated), clock.now()),
            )
        }
    }

    private fun decode(e: AccountEntity): SipAccount? = runCatching {
        json.decodeFromString(SipAccount.serializer(), e.configJson)
    }.getOrNull()
}
