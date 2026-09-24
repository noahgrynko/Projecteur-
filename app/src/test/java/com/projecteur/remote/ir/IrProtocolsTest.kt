package com.projecteur.remote.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolsTest {

    @Test
    fun necFrameHasLeaderThirtyTwoBitsAndStop() {
        val s = IrProtocols.nec(0x04, 0x08)
        assertEquals(38_000, s.carrierHz)
        assertEquals(2 + 64 + 1, s.pattern.size)
        assertEquals(9000, s.pattern[0])
        assertEquals(4500, s.pattern[1])
        // Adresse 0x04 émise poids faible en premier : bits 0,0,1,0,0,0,0,0
        assertEquals(560, s.pattern[3])
        assertEquals(1690, s.pattern[7])
        // Octet inversé de l'adresse (0xFB) : premier bit à 1
        assertEquals(1690, s.pattern[2 + 16 + 1])
    }

    @Test
    fun necExtSendsBytesLittleEndian() {
        val s = IrProtocols.necExt(0x5583, 0x6F90)
        val d = IrDecoder.decode(s.pattern)
        assertEquals(IrDecoder.Decoded("NECext", 0x5583, 0x6F90), d)
    }

    @Test
    fun sircIsRepeatedThreeTimesAt40kHz() {
        val s = IrProtocols.sirc(0x2E, 0x54, 8)
        assertEquals(40_000, s.carrierHz)
        // 1 en-tête + 15 bits × 2 = 31 durées par trame, 2 pauses entre 3 trames
        assertEquals(31 * 3 + 2, s.pattern.size)
        assertEquals(IrDecoder.Decoded("SIRC15", 0x54, 0x2E), IrDecoder.decode(s.pattern))
    }

    @Test
    fun samsungRoundTrip() {
        val s = IrProtocols.samsung32(0x07, 0x02)
        assertEquals(IrDecoder.Decoded("Samsung32", 0x07, 0x02), IrDecoder.decode(s.pattern))
    }

    /**
     * Vérité terrain : une capture brute d'une vraie télécommande Epson (fichier Epson-EB-X12,
     * relevé réel) doit se décoder exactement vers le code « Power » publié sous forme parsée
     * dans les autres fichiers Epson : NECext adresse 83 55, commande 90 6F.
     * Cela valide l'ordre des octets et des bits de l'encodeur face au monde réel.
     */
    @Test
    fun realEpsonCaptureMatchesParsedEpsonPowerCode() {
        val raw = BundledDb.load("Epson/Epson-EB-X12.ir").entries.first { it.name == "POWER" }
        val firstFrame = IrDecoder.trimCapture(raw.data, maxGapUs = 20_000)
        val decoded = IrDecoder.decode(firstFrame)
        assertNotNull(decoded)
        val parsedPower = BundledDb.load("Epson/Epson.ir").entries.first { CommandMapperName.isPower(it.name) && !it.isRaw }
        assertEquals(IrDecoder.Decoded(parsedPower.protocol!!, parsedPower.address, parsedPower.command), decoded)
        // Et notre encodeur produit une trame équivalente à la capture (tolérance 25 %).
        val ours = parsedPower.toSignal().pattern
        assertEquals(firstFrame.size, ours.size)
        ours.indices.forEach { i ->
            assertTrue("durée $i : ${ours[i]} vs ${firstFrame[i]}",
                kotlin.math.abs(ours[i] - firstFrame[i]) <= firstFrame[i] * 0.25)
        }
    }

    @Test
    fun trimCaptureStopsAtLongGapAndEndsWithMark() {
        val t = IrDecoder.trimCapture(intArrayOf(100, 200, 300, 150_000, 400))
        assertEquals(listOf(100, 200, 300), t.toList())
        val t2 = IrDecoder.trimCapture(intArrayOf(100, 200, 300, 400))
        assertEquals(listOf(100, 200, 300), t2.toList())
    }

    @Test(expected = IrProtocols.UnsupportedProtocolException::class)
    fun unknownProtocolIsRefusedNotInvented() {
        IrProtocols.encode("Kaseikyo", 0x325441, 0x05)
    }
}

private object CommandMapperName {
    fun isPower(name: String) = CommandMapper.commandFor(name) == com.projecteur.remote.core.RemoteCommand.POWER
}
