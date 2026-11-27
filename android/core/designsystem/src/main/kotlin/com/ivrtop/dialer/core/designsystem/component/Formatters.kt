package com.ivrtop.dialer.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ivrtop.dialer.core.designsystem.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Israeli conventions: 24-hour clock, day/month/year. */
object Formatters {
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val date = DateTimeFormatter.ofPattern("dd/MM/yy")
    private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun duration(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = time.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = dateTime.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun shortDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = date.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun dayBucket(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (d) {
            today -> 0
            today.minusDays(1) -> 1
            else -> 2
        }
    }
}

@Composable
fun relativeTime(millis: Long): String = when (Formatters.dayBucket(millis)) {
    0 -> Formatters.time(millis)
    1 -> stringResource(R.string.yesterday) + " " + Formatters.time(millis)
    else -> Formatters.shortDate(millis)
}
