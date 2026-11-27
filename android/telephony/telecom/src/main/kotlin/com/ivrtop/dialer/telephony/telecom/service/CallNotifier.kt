package com.ivrtop.dialer.telephony.telecom.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.models.CallDirection
import com.ivrtop.dialer.core.models.CallPhase
import com.ivrtop.dialer.core.models.CallSnapshot
import com.ivrtop.dialer.telephony.telecom.CallUiIntents
import com.ivrtop.dialer.telephony.telecom.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification channels (ids are stable – renaming them loses user customisations):
 *   calls_incoming   HIGH  – full-screen / heads-up CallStyle; sound handled by RingtonePlayer
 *   calls_ongoing    LOW   – ongoing call (foreground service)
 *   calls_missed     DEFAULT
 *   voicemail        DEFAULT
 *   connection       MIN   – "connected and ready" (persistent mode only)
 *   system           DEFAULT – account disabled, config updated
 *   service          LOW   – service messages from the operator
 */
@Singleton
class CallNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val intents: CallUiIntents,
) {
    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        val group = NotificationChannelGroup(GROUP_CALLS, context.getString(R.string.notif_group_calls))
        val sys = context.getSystemService(NotificationManager::class.java)
        sys.createNotificationChannelGroup(group)
        val channels = listOf(
            NotificationChannel(CH_INCOMING, context.getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // RingtonePlayer handles ringing (per-account ringtone)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                this.group = GROUP_CALLS
            },
            NotificationChannel(CH_ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); this.group = GROUP_CALLS; lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT).apply { this.group = GROUP_CALLS },
            NotificationChannel(CH_VOICEMAIL, context.getString(R.string.channel_voicemail), NotificationManager.IMPORTANCE_DEFAULT).apply { this.group = GROUP_CALLS },
            NotificationChannel(CH_CONNECTION, context.getString(R.string.channel_connection), NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
            NotificationChannel(CH_SYSTEM, context.getString(R.string.channel_system), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_SERVICE, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW),
        )
        sys.createNotificationChannels(channels)
    }

    fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canUseFullScreen(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun person(c: CallSnapshot): Person = Person.Builder()
        .setName(c.remoteDisplayName ?: PhoneNumbers.format(c.remoteNumber))
        .setImportant(true)
        .build()

    /** Builds the notification that represents the "foreground" call. */
    fun callNotification(c: CallSnapshot, accountLabel: String?): Notification {
        val isIncomingRinging = c.direction == CallDirection.INCOMING && c.phase.isRinging
        val builder = NotificationCompat.Builder(context, if (isIncomingRinging) CH_INCOMING else CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_ivrtop)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(!isIncomingRinging)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(BRAND_COLOR)
            .setContentIntent(intents.inCall(c.callId))
            .setSubText(accountLabel)

        if (isIncomingRinging) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(person(c), broadcast(ACTION_DECLINE, c.callId), intents.inCall(c.callId, answer = true)))
                .addAction(R.drawable.ic_stat_ivrtop, context.getString(R.string.action_silence), broadcast(ACTION_SILENCE, c.callId))
                .setContentText(context.getString(if (c.phase == CallPhase.INCOMING_PENDING) R.string.call_connecting else R.string.call_incoming))
            if (canUseFullScreen()) builder.setFullScreenIntent(intents.incoming(c.callId), true)
        } else {
            builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(person(c), broadcast(ACTION_HANGUP, c.callId)))
                .addAction(
                    R.drawable.ic_stat_ivrtop,
                    context.getString(if (c.muted) R.string.action_unmute else R.string.action_mute),
                    broadcast(if (c.muted) ACTION_UNMUTE else ACTION_MUTE, c.callId),
                )
            val text = when (c.phase) {
                CallPhase.DIALING, CallPhase.CONNECTING -> R.string.call_dialing
                CallPhase.RINGING_REMOTE -> R.string.call_ringing_remote
                CallPhase.HOLDING -> R.string.call_on_hold
                CallPhase.ANSWERING -> R.string.call_connecting
                else -> R.string.call_active
            }
            builder.setContentText(context.getString(text))
            c.connectedAtMillis?.let { builder.setUsesChronometer(true).setWhen(it).setShowWhen(true) }
        }
        return builder.build()
    }

    /**
     * Fallback when the foreground service could not be started from the background: the
     * CallStyle notification (with full-screen intent) is posted directly so the user still
     * sees and can answer the call. CallService takes over once the app is in the foreground.
     */
    fun postCallDirect(c: CallSnapshot, accountLabel: String?) {
        if (!canPost()) return
        @Suppress("MissingPermission")
        nm.notify(ID_CALL, callNotification(c, accountLabel))
    }

    fun cancelCallNotification() = nm.cancel(ID_CALL)

    fun postMissed(c: CallSnapshot, count: Int) {
        if (!canPost()) return
        val name = c.remoteDisplayName ?: PhoneNumbers.format(c.remoteNumber)
        val n = NotificationCompat.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_ivrtop)
            .setColor(BRAND_COLOR)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentTitle(context.resources.getQuantityString(R.plurals.missed_calls_title, count, count))
            .setContentText(name)
            .setWhen(c.startedAtMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(intents.recents())
            .addAction(R.drawable.ic_stat_ivrtop, context.getString(R.string.action_call_back), intents.callBack(c.remoteNumber, c.accountId))
            .build()
        @Suppress("MissingPermission")
        nm.notify(ID_MISSED, n)
    }

    fun postVoicemail(newMessages: Int) {
        if (!canPost()) return
        if (newMessages <= 0) { nm.cancel(ID_VOICEMAIL); return }
        val n = NotificationCompat.Builder(context, CH_VOICEMAIL)
            .setSmallIcon(R.drawable.ic_stat_ivrtop)
            .setColor(BRAND_COLOR)
            .setContentTitle(context.resources.getQuantityString(R.plurals.voicemail_new, newMessages, newMessages))
            .setContentIntent(intents.voicemail())
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        nm.notify(ID_VOICEMAIL, n)
    }

    fun postSystem(title: String, text: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_SYSTEM)
            .setSmallIcon(R.drawable.ic_stat_ivrtop).setColor(BRAND_COLOR)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intents.main()).setAutoCancel(true).build()
        @Suppress("MissingPermission")
        nm.notify(ID_SYSTEM, n)
    }

    fun connectionNotification(text: String): Notification = NotificationCompat.Builder(context, CH_CONNECTION)
        .setSmallIcon(R.drawable.ic_stat_ivrtop)
        .setColor(BRAND_COLOR)
        .setContentTitle(context.getString(R.string.keepalive_title))
        .setContentText(text)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(intents.main())
        .build()

    fun cancelMissed() = nm.cancel(ID_MISSED)

    private fun broadcast(action: String, callId: String): PendingIntent {
        val i = Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(EXTRA_CALL_ID, callId)
        return PendingIntent.getBroadcast(context, (action + callId).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val GROUP_CALLS = "calls"
        const val CH_INCOMING = "calls_incoming"
        const val CH_ONGOING = "calls_ongoing"
        const val CH_MISSED = "calls_missed"
        const val CH_VOICEMAIL = "voicemail"
        const val CH_CONNECTION = "connection"
        const val CH_SYSTEM = "system"
        const val CH_SERVICE = "service"

        const val ID_CALL = 1001
        const val ID_MISSED = 1002
        const val ID_VOICEMAIL = 1003
        const val ID_KEEPALIVE = 1004
        const val ID_SYSTEM = 1005

        const val ACTION_DECLINE = "com.ivrtop.dialer.action.DECLINE"
        const val ACTION_HANGUP = "com.ivrtop.dialer.action.HANGUP"
        const val ACTION_MUTE = "com.ivrtop.dialer.action.MUTE"
        const val ACTION_UNMUTE = "com.ivrtop.dialer.action.UNMUTE"
        const val ACTION_SILENCE = "com.ivrtop.dialer.action.SILENCE"
        const val EXTRA_CALL_ID = "call_id"

        const val BRAND_COLOR = 0xFF0E78A6.toInt()
    }
}
