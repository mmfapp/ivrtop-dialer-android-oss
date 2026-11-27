package com.ivrtop.dialer.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ivrtop.dialer.MainActivity
import com.ivrtop.dialer.feature.activecall.CallActivity
import com.ivrtop.dialer.telephony.telecom.CallUiIntents
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Explicit, immutable PendingIntents. Distinct request codes avoid extras being merged. */
@Singleton
class CallUiIntentsImpl @Inject constructor(@ApplicationContext private val context: Context) : CallUiIntents {

    private val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    override fun inCall(callId: String, answer: Boolean): PendingIntent =
        PendingIntent.getActivity(context, ("call:$callId:$answer").hashCode(), CallActivity.intent(context, callId, answer), flags)

    override fun incoming(callId: String): PendingIntent =
        PendingIntent.getActivity(context, ("incoming:$callId").hashCode(), CallActivity.intent(context, callId, false), flags)

    override fun recents(): PendingIntent = main(MainActivity.DEST_RECENTS, 11)

    override fun voicemail(): PendingIntent = main(MainActivity.DEST_VOICEMAIL, 12)

    override fun main(): PendingIntent = main(null, 10)

    override fun callBack(number: String, accountId: String?): PendingIntent {
        // Opens the keypad pre-filled (one tap to call) – avoids background call starts from a notification.
        val i = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_DIAL)
            .setData(Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, ("cb:$number").hashCode(), i, flags)
    }

    private fun main(dest: String?, code: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { dest?.let { putExtra(MainActivity.EXTRA_DEST, it) } }
        return PendingIntent.getActivity(context, code, i, flags)
    }
}
