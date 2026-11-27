package com.ivrtop.dialer.core.common.log

/**
 * Scrubs secrets from any text that may reach logcat, diagnostics exports or crash reports.
 * Applied by [RedactingTree] and by the PJSIP log writer.
 */
object Redactor {
    private val rules: List<Pair<Regex, String>> = listOf(
        // SIP Digest Authorization header – keep scheme & username, drop response/nonce/cnonce
        Regex("(?i)((?:Proxy-)?Authorization:\\s*Digest\\s+)[^\\r\\n]*") to "$1<redacted>",
        Regex("(?i)(WWW-Authenticate:\\s*Digest\\s+)[^\\r\\n]*") to "$1<redacted>",
        // passwords in key=value / JSON
        Regex("(?i)(\"?(?:password|passwd|secret|pass|token|deviceToken|fcmToken|pushToken)\"?\\s*[:=]\\s*\"?)[^\",\\s}&]+") to "$1<redacted>",
        // provisioning tokens in URLs
        Regex("(?i)([?&](?:token|t|code)=)[A-Za-z0-9_\\-.~]+") to "$1<redacted>",
        // RFC 8599 push parameters in Contact
        Regex("(?i)(pn-prid=)[^;>\\s]+") to "$1<redacted>",
        Regex("(?i)(pn-param=)[^;>\\s]+") to "$1<redacted>",
        // Bearer tokens
        Regex("(?i)(Bearer\\s+)[A-Za-z0-9_\\-.~+/=]+") to "$1<redacted>",
    )

    fun redact(input: String): String = rules.fold(input) { acc, (re, repl) -> re.replace(acc, repl) }

    /** Phone numbers in diagnostics are partially masked: 0541234567 -> 054***4567 */
    fun maskNumber(number: String): String =
        if (number.length <= 7) number.take(2) + "***" else number.take(3) + "*".repeat(number.length - 7) + number.takeLast(4)
}
