package com.ivrtop.dialer.core.data

import com.ivrtop.dialer.core.common.BuildInfo
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.CodecPreference
import com.ivrtop.dialer.core.models.ProvisionedAccount
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.models.SipTransport
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A parsed setup link. Lives only in memory; the password goes straight to the Keystore on install. */
data class SetupRequest(val account: ProvisionedAccount)

sealed interface SetupLinkResult {
    data class Ok(val request: SetupRequest) : SetupLinkResult
    data class Rejected(val error: AppErrorCode, val detail: String) : SetupLinkResult
}

/**
 * Server-less setup links (docs/AUTO_PROVISIONING.md):
 *
 *   https://ivr.top/dialer/android?user=201&pass=SECRET[&name=…&number=…&transport=tls|tcp|udp&port=…&authuser=…&domain=sip.ivr.top]
 *
 * The same parameters are also accepted after `#` (recommended: a fragment is never sent to any
 * web server, so the password can't end up in access logs). The link opens the app via a verified
 * App Link or is scanned as a QR. Nothing is configured until the user confirms and the
 * registration test succeeds. Each new user@domain is added as an ADDITIONAL account; the same
 * user@domain updates the existing one (no duplicates).
 *
 * Anti-hijack: a link may only target SIP domains in [BuildInfo.linkAllowedDomains] (sip.ivr.top),
 * so a malicious link can't silently route the user's calls through a foreign server.
 */
@Singleton
class ProvisioningRepository @Inject constructor(
    private val accounts: AccountRepository,
    private val buildInfo: BuildInfo,
) {
    fun parseSetupUri(raw: String): SetupLinkResult = parse(raw, buildInfo)

    /** true when an account with the same user@domain already exists (it will be updated). */
    suspend fun isUpdate(req: SetupRequest): Boolean = accounts.findDuplicate(req.account.username, req.account.domain) != null

    /** true when other accounts exist – the new one is added next to them. */
    suspend fun hasOtherAccounts(req: SetupRequest): Boolean =
        accounts.all().any { !(it.username == req.account.username && it.domain.equals(req.account.domain, true)) }

    /** Persists after the registration test passed. Never creates duplicates. */
    suspend fun install(req: SetupRequest, detectedTransport: SipTransport?): SipAccount {
        val p = req.account
        val existing = accounts.findDuplicate(p.username, p.domain)
        val account = toAccount(p, existing?.id ?: UUID.randomUUID().toString(), existing, detectedTransport)
        return accounts.save(account, password = p.password)
    }

    fun toAccount(p: ProvisionedAccount, id: String, existing: SipAccount?, detectedTransport: SipTransport?): SipAccount = SipAccount(
        id = id,
        displayName = p.displayName,
        phoneNumber = p.phoneNumber ?: existing?.phoneNumber,
        username = p.username,
        authUsername = p.authUsername,
        domain = p.domain,
        registrar = p.registrar ?: existing?.registrar,
        outboundProxy = p.outboundProxy ?: existing?.outboundProxy,
        realm = p.realm,
        port = p.port,
        transport = p.transport ?: detectedTransport ?: existing?.transport ?: SipTransport.TLS,
        registrationExpirySec = p.registrationExpirySec,
        srtp = p.srtp,
        dtmfMethod = p.dtmfMethod,
        pushEnabled = false, // no push server
        codecs = existing?.codecs ?: CodecPreference.DEFAULTS,
        voicemailNumber = existing?.voicemailNumber ?: p.voicemailNumber ?: "*97",
        isDefault = existing?.isDefault ?: false,
        colorArgb = existing?.colorArgb ?: nextColor(existing),
        ringtoneUri = existing?.ringtoneUri,
        dnd = existing?.dnd ?: false,
        enabled = true,
    )

    private fun nextColor(existing: SipAccount?): Long = existing?.colorArgb ?: PALETTE[(System.nanoTime() % PALETTE.size).toInt()]

    companion object {
        private val PALETTE = listOf(0xFF0E78A6, 0xFF10B3B5, 0xFF5B5FA8, 0xFFA3418F, 0xFF1F9D55, 0xFFE8A317)
        private val USER_RE = Regex("^[A-Za-z0-9._+\\-]{1,64}$")
        private val NUMBER_RE = Regex("^\\+?[0-9*#]{1,20}$")
        private const val MAX_PASS = 128

        /** Pure parser – unit-tested in ProvisioningLinkTest. */
        fun parse(raw: String, info: BuildInfo): SetupLinkResult {
            val uri = runCatching { URI(raw.trim()) }.getOrNull()
                ?: return SetupLinkResult.Rejected(AppErrorCode.PROVISIONING_TOKEN_INVALID, "not a URL")
            if (!uri.scheme.equals("https", true) || !uri.host.equals(info.appLinkHost, true) ||
                uri.path.trimEnd('/') != info.appLinkPath.trimEnd('/')
            ) {
                return SetupLinkResult.Rejected(AppErrorCode.PROVISIONING_TOKEN_INVALID, "unexpected host/path")
            }
            // Fragment wins over query (both supported; fragment never reaches a web server).
            val params = parseParams(uri.rawQuery) + parseParams(uri.rawFragment)

            val user = params["user"]?.trim().orEmpty()
            val pass = params["pass"].orEmpty()
            if (!USER_RE.matches(user)) return SetupLinkResult.Rejected(AppErrorCode.PROVISIONING_TOKEN_INVALID, "invalid user")
            if (pass.isEmpty() || pass.length > MAX_PASS) return SetupLinkResult.Rejected(AppErrorCode.PROVISIONING_TOKEN_INVALID, "invalid pass")

            val domain = (params["domain"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: info.defaultSipDomain.lowercase())
            if (domain !in info.linkAllowedDomains) {
                return SetupLinkResult.Rejected(AppErrorCode.LINK_DOMAIN_NOT_ALLOWED, domain)
            }
            val transport = params["transport"]?.lowercase()?.let { t ->
                when (t) {
                    "tls" -> SipTransport.TLS
                    "tcp" -> SipTransport.TCP
                    "udp" -> SipTransport.UDP
                    else -> return SetupLinkResult.Rejected(AppErrorCode.PROVISIONING_TOKEN_INVALID, "invalid transport")
                }
            }
            val port = params["port"]?.let { p -> p.toIntOrNull()?.takeIf { it in 1..65535 } ?: return SetupLinkResult.Rejected(AppErrorCode.PROVISIONING_TOKEN_INVALID, "invalid port") }
            val number = params["number"]?.filter { !it.isWhitespace() && it != '-' }?.takeIf { it.isNotEmpty() }
            if (number != null && !NUMBER_RE.matches(number)) return SetupLinkResult.Rejected(AppErrorCode.PROVISIONING_TOKEN_INVALID, "invalid number")
            val authUser = params["authuser"]?.trim()?.takeIf { it.isNotEmpty() }
            if (authUser != null && !USER_RE.matches(authUser)) return SetupLinkResult.Rejected(AppErrorCode.PROVISIONING_TOKEN_INVALID, "invalid authuser")
            val name = params["name"]?.trim()?.take(80)?.takeIf { it.isNotEmpty() }

            return SetupLinkResult.Ok(
                SetupRequest(
                    ProvisionedAccount(
                        displayName = name ?: number ?: user,
                        phoneNumber = number,
                        username = user,
                        authUsername = authUser,
                        password = pass,
                        domain = domain,
                        port = port,
                        transport = transport,
                        pushEnabled = false,
                    ),
                ),
            )
        }

        /**
         * `a=b&c=d` → map. A literal '+' is kept as '+' (passwords often contain it), only %XX is
         * decoded – unlike URLDecoder's form decoding which would turn '+' into a space.
         */
        internal fun parseParams(raw: String?): Map<String, String> {
            if (raw.isNullOrEmpty()) return emptyMap()
            return raw.split('&').mapNotNull { pair ->
                val i = pair.indexOf('=')
                if (i <= 0) return@mapNotNull null
                val key = pair.substring(0, i).lowercase()
                val value = runCatching { URLDecoder.decode(pair.substring(i + 1).replace("+", "%2B"), "UTF-8") }.getOrNull()
                    ?: return@mapNotNull null
                key to value
            }.toMap()
        }
    }
}
