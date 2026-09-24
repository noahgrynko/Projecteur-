package com.projecteur.remote.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressTest {
    private val lan = LocalAddress.Subnet(LocalAddress.parseLiteral("192.168.1.34")!!, 24)

    @Test
    fun onlyPrivateAddressesOfALocalSubnetAreAllowed() {
        assertTrue(LocalAddress.check("192.168.1.20", listOf(lan)) is LocalAddress.Check.Allowed)
        assertTrue(LocalAddress.check("8.8.8.8", listOf(lan)) is LocalAddress.Check.Refused)
        assertTrue(LocalAddress.check("10.0.0.5", listOf(lan)) is LocalAddress.Check.Refused)
        assertTrue(LocalAddress.check("192.168.1.34", listOf(lan)) is LocalAddress.Check.Refused)
        assertTrue(LocalAddress.check("192.168.1.20", emptyList()) is LocalAddress.Check.Refused)
        assertTrue(LocalAddress.check("projecteur.local", listOf(lan)) is LocalAddress.Check.Refused)
    }

    @Test
    fun literalParsingNeverResolvesNames() {
        assertNull(LocalAddress.parseLiteral("example.com"))
        assertNull(LocalAddress.parseLiteral("256.1.1.1"))
        assertNull(LocalAddress.parseLiteral("1.2.3"))
    }

    @Test
    fun broadcastAddress() {
        assertEquals("192.168.1.255", lan.broadcast.hostAddress)
        assertEquals("169.254.255.255",
            LocalAddress.Subnet(LocalAddress.parseLiteral("169.254.10.3")!!, 16).broadcast.hostAddress)
    }
}
