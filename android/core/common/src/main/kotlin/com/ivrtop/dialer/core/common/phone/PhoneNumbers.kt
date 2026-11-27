package com.ivrtop.dialer.core.common.phone

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat

/**
 * Number handling rules (docs/PRODUCT_SPECIFICATION.md §Dialer):
 *  - Extensions (2–6 digits), feature codes (*xx, #xx, *97…) and DTMF strings are sent untouched.
 *  - SIP URIs (contain '@' or start with sip:) are sent untouched.
 *  - Everything else is parsed with libphonenumber for display/matching; for dialing we only
 *    strip formatting characters – we never rewrite the user's digits (PBX dial plans differ).
 */
object PhoneNumbers {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    const val DEFAULT_REGION = "IL"
    private val FORMATTING = Regex("[\\s\\-().\\u200E\\u200F\\u202A-\\u202E]")

    fun isSipUri(input: String): Boolean = input.contains('@') || input.startsWith("sip:", true) || input.startsWith("sips:", true)

    fun isFeatureCode(input: String): Boolean = input.startsWith("*") || input.startsWith("#")

    fun isExtension(input: String): Boolean = input.length in 2..6 && input.all { it.isDigit() }

    /** Removes visual formatting. Keeps + * # , ; and digits (comma/semicolon = pause/wait). */
    fun stripFormatting(input: String): String {
        if (isSipUri(input)) return input.trim()
        return input.replace(FORMATTING, "").filter { it.isDigit() || it in "+*#,;" }
    }

    /** Dial string sent in the SIP Request-URI user part. Pause characters are separated as post-dial DTMF. */
    fun toDialString(input: String): DialString {
        val clean = stripFormatting(input)
        if (isSipUri(clean)) return DialString(clean, null)
        val idx = clean.indexOfFirst { it == ',' || it == ';' }
        return if (idx > 0) DialString(clean.substring(0, idx), clean.substring(idx)) else DialString(clean, null)
    }

    /** E.164 for matching/storage; null for extensions / feature codes / unparsable input. */
    fun normalize(input: String, region: String = DEFAULT_REGION): String? {
        val clean = stripFormatting(input)
        if (clean.isEmpty() || isSipUri(clean) || isFeatureCode(clean) || isExtension(clean)) return null
        return try {
            val parsed = util.parse(clean, region)
            if (util.isPossibleNumber(parsed)) util.format(parsed, PhoneNumberFormat.E164) else null
        } catch (_: NumberParseException) {
            null
        }
    }

    /** Pretty format for display (national format for local numbers). */
    fun format(input: String, region: String = DEFAULT_REGION): String {
        val clean = stripFormatting(input)
        if (clean.isEmpty() || isSipUri(clean) || isFeatureCode(clean) || isExtension(clean)) return clean
        return try {
            val parsed = util.parse(clean, region)
            if (!util.isPossibleNumber(parsed)) return clean
            val fmt = if (util.getRegionCodeForNumber(parsed) == region) PhoneNumberFormat.NATIONAL else PhoneNumberFormat.INTERNATIONAL
            util.format(parsed, fmt)
        } catch (_: NumberParseException) {
            clean
        }
    }

    /** Matching key: last 9 significant digits – robust against 0/+972 prefixes. */
    fun matchKey(input: String): String {
        val digits = stripFormatting(input).filter { it.isDigit() }
        return if (digits.length > 9) digits.takeLast(9) else digits
    }

    fun sameNumber(a: String, b: String): Boolean {
        val ka = matchKey(a)
        return ka.isNotEmpty() && ka == matchKey(b)
    }

    /** Extracts user part from "sip:user@host;params" or "\"Name\" <sip:user@host>". */
    fun userFromSipUri(uri: String): String {
        val inner = uri.substringAfter('<', uri).substringBefore('>')
        val noScheme = inner.removePrefix("sips:").removePrefix("sip:").removePrefix("tel:")
        return noScheme.substringBefore('@').substringBefore(';')
    }

    fun displayNameFromSipUri(uri: String): String? {
        val trimmed = uri.trim()
        if (!trimmed.contains('<')) return null
        return trimmed.substringBefore('<').trim().trim('"').takeIf { it.isNotBlank() }
    }
}

data class DialString(val target: String, val postDial: String?)
