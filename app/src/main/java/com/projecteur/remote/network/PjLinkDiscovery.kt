package com.projecteur.remote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.SocketTimeoutException

/**
 * Recherche PJLink classe 2 : un unique message « %2SRCH » diffusé sur le sous-réseau local
 * (port UDP 4352), prévu par la norme pour découvrir les projecteurs. Aucun balayage d'adresses.
 */
class PjLinkDiscovery(private val inspector: NetworkInspector) {

    data class Found(val address: Inet4Address, val mac: String, val via: NetworkInspector.LocalNetwork)

    suspend fun search(timeoutMs: Int = 3000): List<Found> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Found>()
        for (net in inspector.networks.value) {
            val socket = DatagramSocket(null)
            try {
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(null)
                net.network?.bindSocket(socket)
                socket.soTimeout = 300
                val payload = "%2SRCH\r".toByteArray(Charsets.US_ASCII)
                socket.send(DatagramPacket(payload, payload.size, net.subnet.broadcast, PjLink.PORT))
                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(256)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try { socket.receive(packet) } catch (e: SocketTimeoutException) { continue }
                    val from = packet.address as? Inet4Address ?: continue
                    if (!net.subnet.contains(from) || from == net.subnet.address) continue
                    val mac = PjLink.parseSearchAck(String(packet.data, 0, packet.length, Charsets.US_ASCII)) ?: continue
                    found[from.hostAddress!!] = Found(from, mac, net)
                }
            } catch (_: Exception) {
                // Réseau indisponible ou diffusion interdite : on passe au suivant.
            } finally {
                socket.close()
            }
        }
        found.values.toList()
    }
}
