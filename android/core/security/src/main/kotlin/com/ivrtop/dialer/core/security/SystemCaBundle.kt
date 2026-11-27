package com.ivrtop.dialer.core.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PJSIP is built with OpenSSL, which cannot read the Android trust store directly.
 * We export the *system* CA certificates (AndroidCAStore, "system:" aliases only –
 * user-installed CAs are excluded on purpose) to a PEM bundle that PJSIP verifies against.
 * Certificate validation is therefore always ON; there is no "trust all" mode.
 */
@Singleton
class SystemCaBundle @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureBundle(): File {
        val out = File(context.noBackupFilesDir, "ca-bundle.pem")
        val stamp = File(context.noBackupFilesDir, "ca-bundle.stamp")
        val currentStamp = android.os.Build.FINGERPRINT
        if (out.exists() && stamp.exists() && stamp.readText() == currentStamp) return out

        val ks = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val sb = StringBuilder()
        val aliases = ks.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (!alias.startsWith("system:")) continue
            val cert = ks.getCertificate(alias) as? X509Certificate ?: continue
            sb.append("-----BEGIN CERTIFICATE-----\n")
            sb.append(Base64.encodeToString(cert.encoded, Base64.NO_WRAP).chunked(64).joinToString("\n"))
            sb.append("\n-----END CERTIFICATE-----\n")
        }
        out.writeText(sb.toString())
        stamp.writeText(currentStamp)
        return out
    }
}
