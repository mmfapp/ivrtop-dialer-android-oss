package com.ivrtop.dialer.core.data

import com.google.common.truth.Truth.assertThat
import com.ivrtop.dialer.core.models.BlockKind
import com.ivrtop.dialer.core.models.BlockListType
import com.ivrtop.dialer.core.models.BlockRule
import com.ivrtop.dialer.core.models.DndConfig
import com.ivrtop.dialer.core.models.DndSchedule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PolicyTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun `overnight schedule covers early morning of next day`() {
        // Sunday 22:00 -> Monday 07:00 ; 2026-11-29 is a Sunday (ISO 7)
        val cfg = DndConfig(schedules = listOf(DndSchedule(days = setOf(7), startMinuteOfDay = 22 * 60, endMinuteOfDay = 7 * 60)))
        assertThat(IncomingCallFilter.isDndActive(cfg, "a", at(2026, 11, 29, 23, 0), zone)).isTrue()
        assertThat(IncomingCallFilter.isDndActive(cfg, "a", at(2026, 11, 30, 6, 59), zone)).isTrue()
        assertThat(IncomingCallFilter.isDndActive(cfg, "a", at(2026, 11, 30, 7, 0), zone)).isFalse()
        assertThat(IncomingCallFilter.isDndActive(cfg, "a", at(2026, 11, 29, 21, 59), zone)).isFalse()
    }

    @Test fun `timed global dnd expires`() {
        val now = at(2026, 11, 26, 12, 0)
        assertThat(IncomingCallFilter.isDndActive(DndConfig(enabledGlobally = true, until = now + 1000), null, now, zone)).isTrue()
        assertThat(IncomingCallFilter.isDndActive(DndConfig(enabledGlobally = true, until = now - 1), null, now, zone)).isFalse()
    }

    private fun rule(p: String, k: BlockKind, l: BlockListType = BlockListType.DENY) = BlockRule(0, p, k, l, null, null, false, 0)

    @Test fun `block rules with allow override`() {
        val rules = listOf(rule("03", BlockKind.PREFIX), rule("035551234", BlockKind.EXACT, BlockListType.ALLOW), rule("anonymous", BlockKind.ANONYMOUS))
        assertThat(BlockRepository.evaluate("035559999", rules)).isTrue()
        assertThat(BlockRepository.evaluate("+97235559999", rules)).isTrue()
        assertThat(BlockRepository.evaluate("035551234", rules)).isFalse()
        assertThat(BlockRepository.evaluate("anonymous", rules)).isTrue()
        assertThat(BlockRepository.evaluate("0501234567", rules)).isFalse()
    }
}
