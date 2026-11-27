package com.ivrtop.dialer.telephony.telecom

import android.app.PendingIntent

/**
 * Provided by :app so the telephony layer can open UI without depending on activities.
 * All PendingIntents must be FLAG_IMMUTABLE and explicit (component set).
 */
interface CallUiIntents {
    /** Opens the in-call screen. When [answer] is true the activity answers [callId] on launch. */
    fun inCall(callId: String, answer: Boolean = false): PendingIntent
    /** Full-screen incoming call activity (shown over the lock screen). */
    fun incoming(callId: String): PendingIntent
    fun recents(): PendingIntent
    fun voicemail(): PendingIntent
    fun main(): PendingIntent
    fun callBack(number: String, accountId: String?): PendingIntent
}
