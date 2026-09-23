package com.projecteur.remote.network

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Règles de sécurité réseau : l'application ne contacte que des adresses IPv4 privées situées
 * dans un sous-réseau auquel le téléphone est directement raccordé. Aucune adresse Internet,
 * aucun balayage de plage.
 */
object LocalAddress {

    data class Subnet(val address: Inet4Address, val prefixLength: Int) {
        private val mask: Int get() = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
        val network: Int get() = toInt(address) and mask
        val broadcast: Inet4Address get() = fromInt(network or mask.inv())
        fun contains(ip: Inet4Address) = (toInt(ip) and mask) == network
        override fun toString() = "${address.hostAddress}/$prefixLength"
    }

    fun toInt(a: Inet4Address): Int = a.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    fun fromInt(v: Int): Inet4Address = InetAddress.getByAddress(
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    ) as Inet4Address

    /** Analyse une IPv4 littérale sans jamais faire de requête DNS. */
    fun parseLiteral(text: String): Inet4Address? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        val bytes = parts.map { p -> p.toIntOrNull()?.takeIf { it in 0..255 && p.isNotEmpty() && p.length <= 3 } ?: return null }
        return InetAddress.getByAddress(bytes.map { it.toByte() }.toByteArray()) as Inet4Address
    }

    fun isPrivate(a: Inet4Address): Boolean {
        val b0 = a.address[0].toInt() and 0xFF
        val b1 = a.address[1].toInt() and 0xFF
        return b0 == 10 || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168) || (b0 == 169 && b1 == 254)
    }

    sealed class Check {
        data class Allowed(val address: Inet4Address, val subnet: Subnet) : Check()
        data class Refused(val reason: String) : Check()
    }

    fun check(text: String, localSubnets: List<Subnet>): Check {
        val ip = parseLiteral(text) ?: return Check.Refused("Adresse IPv4 invalide (ex. 192.168.1.20). Les noms d'hôte ne sont pas acceptés.")
        if (!isPrivate(ip)) return Check.Refused("Adresse non privée refusée : l'application ne contacte jamais Internet.")
        if (localSubnets.isEmpty()) return Check.Refused("Aucune interface réseau locale détectée.")
        val subnet = localSubnets.firstOrNull { it.contains(ip) }
            ?: return Check.Refused("Adresse hors des réseaux locaux du téléphone (${localSubnets.joinToString()}).")
        if (ip == subnet.address) return Check.Refused("C'est l'adresse du téléphone lui-même.")
        return Check.Allowed(ip, subnet)
    }
}
