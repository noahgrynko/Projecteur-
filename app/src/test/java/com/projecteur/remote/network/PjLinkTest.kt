package com.projecteur.remote.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PjLinkTest {
    @Test
    fun authDigestMatchesSpecificationExample() {
        // Exemple de la spécification PJLink : aléa « 498e4a67 », mot de passe « JBMIAProjectorLink ».
        assertEquals("5d8409bc1c3fa39749434aa3a5c38682", PjLink.authDigest("498e4a67", "JBMIAProjectorLink"))
    }

    @Test
    fun greetingAndReplies() {
        assertEquals(PjLink.Greeting.NoAuth, PjLink.parseGreeting("PJLINK 0"))
        assertEquals(PjLink.Greeting.Auth("498e4a67"), PjLink.parseGreeting("PJLINK 1 498e4a67"))
        assertTrue(PjLink.parseGreeting("HTTP/1.1 400") is PjLink.Greeting.Invalid)
        assertEquals(PjLink.Reply.Ok("1"), PjLink.parseReply("%1POWR=1", "POWR"))
        assertEquals(PjLink.Reply.Ok("OK"), PjLink.parseReply("%2SVOL=OK", "SVOL"))
        assertEquals("ERR3", (PjLink.parseReply("%1POWR=ERR3", "POWR") as PjLink.Reply.Error).code)
        assertEquals("ERRA", (PjLink.parseReply("PJLINK ERRA", "POWR") as PjLink.Reply.Error).code)
        assertEquals("%1POWR ?\r", PjLink.command(1, "POWR", "?"))
    }

    @Test
    fun inputsAndSearch() {
        assertEquals(listOf("11", "31", "32"), PjLink.parseInputs("11 31 32"))
        assertEquals("00:11:22:33:44:55", PjLink.parseSearchAck("%2ACKN=00:11:22:33:44:55\r"))
    }
}
