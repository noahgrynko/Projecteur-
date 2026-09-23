package com.projecteur.remote.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Recense les réseaux locaux réellement disponibles (Wi-Fi sans Internet, réseau créé par le
 * projecteur, adaptateur Ethernet USB-C, point d'accès du téléphone). Les réseaux mobiles et
 * VPN sont exclus : ils ne mènent pas au projecteur.
 */
class NetworkInspector(context: Context) {

    data class LocalNetwork(
        val label: String,
        val interfaceName: String,
        val subnet: LocalAddress.Subnet,
        /** Réseau Android auquel lier les sockets (null pour le point d'accès du téléphone). */
        val network: Network?,
        val hasInternet: Boolean,
    )

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _networks = MutableStateFlow<List<LocalNetwork>>(emptyList())
    val networks: StateFlow<List<LocalNetwork>> = _networks.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = refresh()
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        refresh()
    }

    @Suppress("DEPRECATION")
    fun refresh() {
        val result = ArrayList<LocalNetwork>()
        val seenIfaces = HashSet<String>()
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val kind = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> continue
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> continue
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet (USB-C)"
                else -> continue
            }
            val lp = cm.getLinkProperties(network) ?: continue
            val iface = lp.interfaceName ?: continue
            for (la in lp.linkAddresses) {
                val a = la.address as? Inet4Address ?: continue
                if (!LocalAddress.isPrivate(a)) continue
                seenIfaces += iface
                result += LocalNetwork(
                    label = kind, interfaceName = iface,
                    subnet = LocalAddress.Subnet(a, la.prefixLength), network = network,
                    hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }
        }
        // Point d'accès créé par le téléphone : pas d'objet Network Android, on lit l'interface.
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces().toList()) {
                if (!ni.isUp || ni.isLoopback || ni.name in seenIfaces) continue
                if (!(ni.name.startsWith("ap") || ni.name.startsWith("swlan") || ni.name.startsWith("softap") || ni.name.startsWith("wlan"))) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address as? Inet4Address ?: continue
                    if (!LocalAddress.isPrivate(a)) continue
                    result += LocalNetwork("Point d'accès du téléphone", ni.name,
                        LocalAddress.Subnet(a, ia.networkPrefixLength.toInt()), null, false)
                }
            }
        }
        _networks.value = result
    }

    fun subnets(): List<LocalAddress.Subnet> = _networks.value.map { it.subnet }

    fun networkFor(subnet: LocalAddress.Subnet): LocalNetwork? = _networks.value.firstOrNull { it.subnet == subnet }
}
