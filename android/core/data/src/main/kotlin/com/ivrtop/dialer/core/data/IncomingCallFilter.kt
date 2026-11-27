package com.ivrtop.dialer.core.data

import com.ivrtop.dialer.core.common.Clock
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.CallEndReason
import com.ivrtop.dialer.core.models.DndAction
import com.ivrtop.dialer.core.models.DndConfig
import com.ivrtop.dialer.core.models.IncomingDecision
import com.ivrtop.dialer.core.models.SipAccount
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides what to do with an incoming call BEFORE the user is alerted.
 * Order: block list → account disabled for incoming → DND (app-local) → ring.
 * The Android system DND (Zen) is respected separately by the notification channel.
 */
@Singleton
class IncomingCallFilter @Inject constructor(
    private val blocks: BlockRepository,
    private val favorites: FavoritesRepository,
    private val history: CallHistoryRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun decide(account: SipAccount, number: String): IncomingDecision {
        if (BlockRepository.evaluate(number, blocks.all())) return IncomingDecision.Reject(603, CallEndReason.BLOCKED)
        if (!account.acceptIncoming || !account.enabled) return IncomingDecision.Reject(480, CallEndReason.UNAVAILABLE)

        val cfg = settings.current().dnd
        val dndActive = account.dnd || isDndActive(cfg, account.id, clock.now())
        if (!dndActive) return IncomingDecision.Ring

        if (cfg.allowFavorites && favorites.isFavorite(number)) return IncomingDecision.Ring
        if (cfg.allowRepeatCallers) {
            val since = clock.now() - cfg.repeatCallerWindowMin * 60_000L
            if (history.recentMissedCount(number, since) > 0) return IncomingDecision.Ring
        }
        return when (cfg.action) {
            DndAction.SILENCE -> IncomingDecision.RingSilently
            DndAction.REJECT -> IncomingDecision.Reject(603, CallEndReason.REJECTED_DND)
            DndAction.VOICEMAIL -> IncomingDecision.Reject(486, CallEndReason.REJECTED_DND)
            DndAction.FORWARD -> cfg.forwardTo?.takeIf { it.isNotBlank() }?.let { IncomingDecision.Forward(it) }
                ?: IncomingDecision.Reject(486, CallEndReason.REJECTED_DND)
        }
    }

    companion object {
        fun isDndActive(cfg: DndConfig, accountId: String?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
            if (cfg.enabledGlobally && (cfg.until?.let { it > nowMillis } ?: true)) return true
            val t = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val minute = t.hour * 60 + t.minute
            val day = t.dayOfWeek.value
            return cfg.schedules.any { s ->
                if (!s.enabled) return@any false
                if (s.accountId != null && s.accountId != accountId) return@any false
                if (s.startMinuteOfDay <= s.endMinuteOfDay) {
                    day in s.days && minute in s.startMinuteOfDay until s.endMinuteOfDay
                } else {
                    // overnight window e.g. 22:00–07:00: belongs to the start day
                    val prevDay = if (day == 1) 7 else day - 1
                    (day in s.days && minute >= s.startMinuteOfDay) || (prevDay in s.days && minute < s.endMinuteOfDay)
                }
            }
        }
    }
}
