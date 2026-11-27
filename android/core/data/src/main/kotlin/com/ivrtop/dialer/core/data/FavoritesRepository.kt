package com.ivrtop.dialer.core.data

import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.database.FavoriteDao
import com.ivrtop.dialer.core.database.FavoriteEntity
import com.ivrtop.dialer.core.models.Favorite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(private val dao: FavoriteDao) {

    val favorites: Flow<List<Favorite>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    suspend fun add(displayName: String, number: String, contactId: String?, photoUri: String?) {
        if (dao.getAll().any { PhoneNumbers.sameNumber(it.number, number) }) return
        dao.upsert(
            FavoriteEntity(contactId = contactId, displayName = displayName, number = number, photoUri = photoUri,
                preferredAccountId = null, position = dao.nextPosition(), speedDialDigit = null, blfEnabled = false),
        )
    }

    suspend fun isFavorite(number: String): Boolean = dao.getAll().any { PhoneNumbers.sameNumber(it.number, number) }

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    suspend fun update(f: Favorite) = dao.upsert(f.toEntity())

    /** Assigns digit 2..9, removing it from any other favorite first. */
    suspend fun assignSpeedDial(id: Long, digit: Int?) {
        require(digit == null || digit in 2..9)
        dao.getAll().forEach { e ->
            if (digit != null && e.speedDialDigit == digit && e.id != id) dao.upsert(e.copy(speedDialDigit = null))
            if (e.id == id) dao.upsert(e.copy(speedDialDigit = digit))
        }
    }

    suspend fun speedDial(digit: Int): Favorite? = dao.bySpeedDial(digit)?.toModel()

    private fun FavoriteEntity.toModel() = Favorite(id, contactId, displayName, number, photoUri, preferredAccountId, position, speedDialDigit, blfEnabled)
    private fun Favorite.toEntity() = FavoriteEntity(id, contactId, displayName, number, photoUri, preferredAccountId, position, speedDialDigit, blfEnabled)
}
