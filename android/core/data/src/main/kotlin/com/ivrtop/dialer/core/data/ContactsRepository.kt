package com.ivrtop.dialer.core.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import androidx.core.content.ContextCompat
import com.ivrtop.dialer.core.common.ApplicationScope
import com.ivrtop.dialer.core.common.IoDispatcher
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.common.phone.T9
import com.ivrtop.dialer.core.database.LocalContactDao
import com.ivrtop.dialer.core.models.Contact
import com.ivrtop.dialer.core.models.ContactPhone
import com.ivrtop.dialer.core.models.ContactSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class ContactMatch(val contact: Contact, val phone: ContactPhone?, val score: Int)

/**
 * Device contacts are read locally only (READ_CONTACTS). Nothing is uploaded unless the user
 * explicitly enables "sync contacts with organisation directory" (AppSettings.contactsUploadConsent).
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDao: LocalContactDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val json = Json { ignoreUnknownKeys = true }
    private val permissionTick = MutableStateFlow(0)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Call after the permission dialog closes. */
    fun onPermissionChanged() { permissionTick.value++ }

    private val deviceContacts: Flow<List<Contact>> = permissionTick.flatMapLatest {
        if (!hasPermission()) flowOf(emptyList()) else observeUri(ContactsContract.Contacts.CONTENT_URI).map { loadDevice() }
    }.flowOn(io)

    private val localContacts: Flow<List<Contact>> = localDao.observeAll().map { rows ->
        rows.map { r ->
            Contact(
                id = "local:${r.id}",
                source = ContactSource.valueOf(r.source),
                displayName = r.displayName,
                phones = runCatching { json.decodeFromString(ListSerializer(ContactPhone.serializer()), r.phonesJson) }.getOrDefault(emptyList()),
            )
        }
    }

    val contacts: StateFlow<List<Contact>> = combine(deviceContacts, localContacts) { d, l ->
        (d + l).sortedWith(compareBy(java.text.Collator.getInstance()) { it.displayName })
    }.stateIn(scope, SharingStarted.WhileSubscribed(30_000), emptyList())

    /** number → contact index for fast caller-id (rebuilt on every contacts emission). */
    private val numberIndex: StateFlow<Map<String, Pair<Contact, ContactPhone>>> = contacts.map { list ->
        buildMap {
            list.forEach { c -> c.phones.forEach { p -> putIfAbsent(PhoneNumbers.matchKey(p.number), c to p) } }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(30_000), emptyMap())

    /** Caller-id lookup. Uses PhoneLookup (fast, indexed) and falls back to the local index. */
    suspend fun lookup(number: String): Contact? = kotlinx.coroutines.withContext(io) {
        if (number.isBlank()) return@withContext null
        numberIndex.value[PhoneNumbers.matchKey(number)]?.first?.let { return@withContext it }
        if (!hasPermission()) return@withContext null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        resolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.STARRED, ContactsContract.PhoneLookup.LOOKUP_KEY),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                Contact(
                    id = c.getLong(0).toString(), source = ContactSource.DEVICE, displayName = c.getString(1) ?: number,
                    phones = listOf(ContactPhone(number, PhoneNumbers.normalize(number), null)),
                    photoUri = c.getString(2), thumbnailUri = c.getString(3), starred = c.getInt(4) == 1, lookupKey = c.getString(5),
                )
            } else null
        }
    }

    /** Name / number / T9 search used by the dialer suggestions and contacts search. */
    fun search(query: String, t9: Boolean): List<ContactMatch> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val digits = PhoneNumbers.stripFormatting(q).filter { it.isDigit() }
        val lower = q.lowercase()
        return contacts.value.mapNotNull { c ->
            val nameScore = when {
                t9 && digits.isNotEmpty() -> T9.score(c.displayName, digits)
                c.displayName.lowercase().startsWith(lower) -> 100
                c.displayName.lowercase().split(' ').any { it.startsWith(lower) } -> 80
                c.displayName.lowercase().contains(lower) -> 40
                else -> -1
            }
            val phone = if (digits.length >= 2) c.phones.firstOrNull { p ->
                val pd = PhoneNumbers.stripFormatting(p.number)
                pd.contains(digits) || (p.normalized?.contains(digits) == true)
            } else null
            val numberScore = if (phone != null) 90 else -1
            val best = maxOf(nameScore, numberScore)
            if (best < 0) null else ContactMatch(c, phone ?: c.phones.firstOrNull(), best)
        }.sortedWith(compareByDescending<ContactMatch> { it.score }.thenBy { it.contact.displayName }).take(50)
    }

    private fun loadDevice(): List<Contact> {
        val byId = LinkedHashMap<Long, Contact>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.NORMALIZED_NUMBER, Phone.TYPE, Phone.LABEL,
                Phone.IS_SUPER_PRIMARY, Phone.PHOTO_URI, Phone.PHOTO_THUMBNAIL_URI, Phone.STARRED, Phone.LOOKUP_KEY),
            null, null, Phone.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(2) ?: continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(4), c.getString(5)).toString()
                val phone = ContactPhone(number, c.getString(3) ?: PhoneNumbers.normalize(number), label, c.getInt(6) == 1)
                val existing = byId[id]
                if (existing == null) {
                    byId[id] = Contact(
                        id = id.toString(), source = ContactSource.DEVICE, displayName = c.getString(1) ?: number,
                        phones = listOf(phone), photoUri = c.getString(7), thumbnailUri = c.getString(8),
                        starred = c.getInt(9) == 1, lookupKey = c.getString(10),
                    )
                } else if (existing.phones.none { PhoneNumbers.sameNumber(it.number, number) }) {
                    byId[id] = existing.copy(phones = existing.phones + phone)
                }
            }
        }
        // SIP addresses stored on contacts
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, SipAddress.SIP_ADDRESS),
            "${ContactsContract.Data.MIMETYPE} = ?", arrayOf(SipAddress.CONTENT_ITEM_TYPE), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val sip = c.getString(1) ?: continue
                byId[id]?.let { byId[id] = it.copy(sipAddresses = it.sipAddresses + sip) }
            }
        }
        return byId.values.toList()
    }

    private fun observeUri(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(uri, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().onStart { emit(Unit) }
}
