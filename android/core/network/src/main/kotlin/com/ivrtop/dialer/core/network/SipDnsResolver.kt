package com.ivrtop.dialer.core.network

import com.ivrtop.dialer.core.common.IoDispatcher
import com.ivrtop.dialer.core.models.ProbeCandidate
import com.ivrtop.dialer.core.models.SipTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.minidns.hla.ResolverApi
import org.minidns.record.SRV
import timber.log.Timber
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RFC 3263 style server location used by the auto-configuration wizard:
 *  1. SRV    (_sips._tcp, _sip._tcp, _sip._udp)
 *  2. A/AAAA fallback with default ports
 * Candidates are ordered: encrypted first, then SRV before A, then SRV priority/weight.
 *
 * NAPTR: minidns has no NAPTR record type, so NAPTR is not queried. In practice SRV gives
 * the same information for Asterisk deployments; documented in docs/SIP_INTEGRATION.md.
 */
@Singleton
class SipDnsResolver @Inject constructor(@IoDispatcher private val io: CoroutineDispatcher) {

    suspend fun hostResolves(host: String): Boolean = withContext(io) {
        try { InetAddress.getAllByName(host).isNotEmpty() } catch (_: Exception) { false }
    }

    suspend fun locate(domain: String): List<ProbeCandidate> = withContext(io) {
        val result = mutableListOf<ProbeCandidate>()

        // SRV
        val srvNames = listOf(
            SipTransport.TLS to "_sips._tcp.$domain",
            SipTransport.TCP to "_sip._tcp.$domain",
            SipTransport.UDP to "_sip._udp.$domain",
        )
        for ((transport, name) in srvNames) {
            try {
                val res = ResolverApi.INSTANCE.resolve(name, SRV::class.java)
                if (res.wasSuccessful()) {
                    res.answers.sortedWith(compareBy<SRV>({ it.priority }, { -it.weight })).forEach { srv ->
                        result += ProbeCandidate(transport, srv.target.toString().trimEnd('.'), srv.port, "SRV")
                    }
                }
            } catch (e: Exception) {
                Timber.d("SRV lookup failed for $name: ${e.javaClass.simpleName}")
            }
        }

        // A/AAAA fallback
        if (hostResolves(domain)) {
            result += ProbeCandidate(SipTransport.TLS, domain, 5061, "A")
            result += ProbeCandidate(SipTransport.TCP, domain, 5060, "A")
            result += ProbeCandidate(SipTransport.UDP, domain, 5060, "A")
        }

        result.distinctBy { Triple(it.transport, it.host, it.port) }
            .sortedWith(
                compareBy<ProbeCandidate>(
                    { if (it.transport.encrypted) 0 else 1 },
                    { if (it.source == "SRV") 0 else 1 },
                ),
            )
    }
}
