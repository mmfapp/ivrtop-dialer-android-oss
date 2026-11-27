package com.ivrtop.dialer.core.common

import kotlin.math.min
import kotlin.random.Random

/**
 * Exponential backoff with full jitter. Auth failures use a much longer base so that
 * repeated wrong passwords never trigger Fail2Ban on the PBX (see docs/SIP_INTEGRATION.md).
 */
class Backoff(
    private val baseMs: Long = 2_000,
    private val maxMs: Long = 5 * 60_000,
    private val random: Random = Random.Default,
) {
    private var attempt = 0

    fun next(): Long {
        val exp = baseMs * (1L shl min(attempt, 16))
        attempt++
        val capped = min(exp, maxMs)
        return capped / 2 + random.nextLong(capped / 2 + 1)
    }

    fun reset() { attempt = 0 }

    val attempts: Int get() = attempt
}
