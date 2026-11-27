package com.ivrtop.dialer.core.common.phone

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emergency numbers are NEVER routed over SIP by default (VoIP cannot guarantee location
 * delivery or availability). The dialer hands them to the native cellular dialer.
 */
@Singleton
class EmergencyNumbers @Inject constructor(@ApplicationContext private val context: Context) {

    private val israel = setOf("100", "101", "102", "103", "104", "105", "106", "110", "112", "118", "911", "999", "000", "08")

    fun isEmergency(number: String): Boolean {
        val n = PhoneNumbers.stripFormatting(number)
        if (n.isEmpty()) return false
        val tm = context.getSystemService(TelephonyManager::class.java)
        if (tm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (tm.isEmergencyNumber(n)) return true
            } catch (_: Exception) {
                // isEmergencyNumber may throw on devices without telephony
            }
        }
        return n in israel
    }
}
