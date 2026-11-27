package com.ivrtop.dialer.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.ivrtop.dialer.core.common.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkType { NONE, WIFI, CELLULAR, ETHERNET, VPN, OTHER }

/**
 * A change of [networkId] or [localAddresses] while a call/registration is active means
 * the IP changed (Wi-Fi ⇄ LTE); SipEngine.handleIpChange() is triggered by the runtime.
 */
data class NetworkState(
    val type: NetworkType,
    val networkId: String?,
    val validated: Boolean,
    val metered: Boolean,
    val hasIpv6: Boolean,
    val hasIpv4: Boolean,
    val localAddresses: Set<String>,
) {
    val isConnected: Boolean get() = type != NetworkType.NONE

    companion object {
        val NONE = NetworkState(NetworkType.NONE, null, false, false, false, false, emptySet())
    }
}

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    val state: StateFlow<NetworkState> = callbackFlow {
        fun publish() { trySend(snapshot()) }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish()
            override fun onLost(network: Network) = publish()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = publish()
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = publish()
        }
        // Default network callback reflects the network apps actually use (handles VPN, Wi-Fi/LTE switch).
        cm.registerDefaultNetworkCallback(cb)
        publish()
        awaitClose { cm.unregisterNetworkCallback(cb) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, snapshot())

    fun snapshot(): NetworkState {
        val net = cm.activeNetwork ?: return NetworkState.NONE
        val caps = cm.getNetworkCapabilities(net) ?: return NetworkState.NONE
        val lp = cm.getLinkProperties(net)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
        val addrs = lp?.linkAddresses?.map { it.address }.orEmpty()
        return NetworkState(
            type = type,
            networkId = net.toString(),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            hasIpv6 = addrs.any { it is java.net.Inet6Address && !it.isLinkLocalAddress },
            hasIpv4 = addrs.any { it is java.net.Inet4Address },
            localAddresses = addrs.filterNot { it.isLinkLocalAddress }.mapNotNull { it.hostAddress }.toSet(),
        )
    }
}
