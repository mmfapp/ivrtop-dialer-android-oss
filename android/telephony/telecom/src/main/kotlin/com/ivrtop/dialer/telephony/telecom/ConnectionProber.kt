package com.ivrtop.dialer.telephony.telecom

import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.ProbeCandidate
import com.ivrtop.dialer.core.models.ProbeResult
import com.ivrtop.dialer.core.models.ProbeStep
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.models.SipTransport
import com.ivrtop.dialer.core.network.NetworkMonitor
import com.ivrtop.dialer.core.network.SipDnsResolver
import com.ivrtop.dialer.telephony.sip.AccountSecrets
import com.ivrtop.dialer.telephony.sip.ConnectionTester
import com.ivrtop.dialer.telephony.sip.ProbeOutcome
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-detects working connection settings from just server + username + password
 * (docs/AUTO_PROVISIONING.md §Auto-detect).
 *
 * Safety rules:
 *  - Encrypted transports are tried first. Plain TCP/UDP only when [allowInsecure] is true
 *    (explicit user/policy choice) – never a silent downgrade.
 *  - A final 401/403/407 stops the whole run immediately: at most ONE failed authentication
 *    per attempt, so Fail2Ban / Asterisk security policies are never triggered.
 *  - Max [MAX_CANDIDATES] transport attempts, each a single REGISTER with timeout.
 */
@Singleton
class ConnectionProber @Inject constructor(
    private val dns: SipDnsResolver,
    private val network: NetworkMonitor,
    private val engineStarter: EngineStarter,
) : ConnectionTester {
    private val hostRe = Regex("^(?=.{1,253}$)([a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$|^\\d{1,3}(\\.\\d{1,3}){3}$|^\\[?[0-9a-fA-F:]+]?$")

    override suspend fun detect(
        base: SipAccount,
        password: String,
        allowInsecure: Boolean,
        fixedTransport: SipTransport?,
        onStep: (ProbeStep) -> Unit,
    ): ProbeResult {
        val tried = mutableListOf<String>()
        val domain = base.domain.trim().lowercase()

        if (!network.state.value.isConnected) return ProbeResult.Failure(AppErrorCode.NETWORK_UNAVAILABLE, "no network", tried)
        if (!hostRe.matches(domain)) return ProbeResult.Failure(AppErrorCode.DNS_FAILURE, "invalid host: $domain", tried)

        onStep(ProbeStep.LOCATE_SERVER)
        val candidates: List<ProbeCandidate>
        try {
            if (!dns.hostResolves(base.registrar?.takeIf { it.isNotBlank() } ?: domain)) {
                return ProbeResult.Failure(AppErrorCode.DNS_FAILURE, "DNS lookup failed for $domain", tried)
            }
            candidates = when {
                fixedTransport != null -> listOf(ProbeCandidate(fixedTransport, base.registrar ?: domain, base.port ?: fixedTransport.defaultPort, "fixed"))
                else -> dns.locate(domain).ifEmpty { listOf(ProbeCandidate(SipTransport.TLS, domain, 5061, "default")) }
            }.filter { allowInsecure || it.transport.encrypted || fixedTransport != null }.take(MAX_CANDIDATES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.w(e, "DNS lookup failed")
            return ProbeResult.Failure(AppErrorCode.DNS_FAILURE, "DNS: ${e.describe()}", tried)
        }

        if (candidates.isEmpty()) {
            return ProbeResult.Failure(AppErrorCode.TLS_HANDSHAKE_FAILED, "no encrypted transport offered and insecure fallback disabled", tried)
        }

        val engine = try {
            engineStarter.ensureStarted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // e.g. UnsatisfiedLinkError (native lib) or a PJSIP init error – report, never crash.
            Timber.e(e, "SIP engine failed to start")
            return ProbeResult.Failure(AppErrorCode.ENGINE_NOT_READY, "engine start: ${e.describe()}", tried)
        }
        onStep(ProbeStep.CHECK_CREDENTIALS)
        var lastError: String? = null
        for (cand in candidates) {
            val label = "${cand.transport.wire.uppercase()} ${cand.host}:${cand.port} (${cand.source})"
            tried += label
            val acc = base.copy(
                transport = cand.transport,
                registrar = if (cand.host.equals(domain, true)) base.registrar else cand.host,
                port = cand.port.takeIf { it != cand.transport.defaultPort },
            )
            val out = try {
                engine.probeRegistration(acc, AccountSecrets(password), PROBE_TIMEOUT_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // PJSIP reports config/transport problems as exceptions – try the next candidate.
                Timber.w(e, "probe failed via $label")
                lastError = "$label: ${e.describe()}"
                continue
            }
            when (out) {
                is ProbeOutcome.Registered -> {
                    onStep(ProbeStep.CHECK_CONNECTION)
                    onStep(ProbeStep.DONE)
                    return ProbeResult.Success(cand, out.elapsedMs)
                }
                is ProbeOutcome.Rejected -> {
                    val code = when (out.code) {
                        401, 407 -> AppErrorCode.AUTH_FAILED
                        403 -> AppErrorCode.FORBIDDEN
                        404 -> AppErrorCode.NOT_FOUND
                        else -> null
                    }
                    if (code != null) return ProbeResult.Failure(code, "${out.code} ${out.reason} via $label", tried)
                    // Other final errors (e.g. 503) → try next transport.
                }
                is ProbeOutcome.TransportError, ProbeOutcome.Timeout -> Unit
            }
        }
        return ProbeResult.Failure(
            if (lastError != null) AppErrorCode.UNKNOWN else AppErrorCode.SERVER_UNREACHABLE,
            lastError ?: "no transport succeeded",
            tried,
        )
    }

    /** Short technical description for the error screen (exception messages never contain the password). */
    private fun Throwable.describe(): String = "${javaClass.simpleName}: ${message.orEmpty().take(300)}"

    private companion object {
        const val MAX_CANDIDATES = 4
        const val PROBE_TIMEOUT_MS = 12_000L
    }
}
