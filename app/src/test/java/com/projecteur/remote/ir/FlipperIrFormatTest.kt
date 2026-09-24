package com.projecteur.remote.ir

import com.projecteur.remote.core.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlipperIrFormatTest {
    private val sample = """
        Filetype: IR signals file
        Version: 1
        # Télécommande d'essai
        #
        name: Power
        type: parsed
        protocol: NECext
        address: 83 55 00 00
        command: 90 6F 00 00
        #
        name: Vol_up
        type: raw
        frequency: 38000
        duty_cycle: 0.330000
        data: 9000 4500 560 560 560
        #
        name: Broken
        type: parsed
        protocol: NEC
    """.trimIndent()

    @Test
    fun parsesParsedAndRawEntriesAndReportsBrokenOnes() {
        val r = FlipperIrFormat.parse(sample)
        assertEquals(2, r.entries.size)
        assertEquals(0x5583L, r.entries[0].address)
        assertEquals(0x6F90L, r.entries[0].command)
        assertEquals(listOf(9000, 4500, 560, 560, 560), r.entries[1].data.toList())
        assertEquals(1, r.warnings.size)
    }

    @Test
    fun writeThenParseIsLossless() {
        val entries = FlipperIrFormat.parse(sample).entries
        val again = FlipperIrFormat.parse(FlipperIrFormat.write(entries, "test")).entries
        assertEquals(entries, again)
    }

    @Test
    fun rawPatternEndingWithSpaceIsTrimmedToMark() {
        val e = FlipperIrFormat.Entry("x", "raw", data = intArrayOf(100, 200, 300, 400))
        assertEquals(3, e.toSignal().pattern.size)
    }

    @Test
    fun commandNamesAreMappedConservatively() {
        assertEquals(RemoteCommand.VOL_UP, CommandMapper.commandFor("VOL+"))
        assertEquals(RemoteCommand.VOL_DOWN, CommandMapper.commandFor("Vol_dn"))
        assertEquals(RemoteCommand.BLANK, CommandMapper.commandFor("A/V Mute"))
        assertEquals(RemoteCommand.EXIT, CommandMapper.commandFor("Esc"))
        assertEquals(RemoteCommand.OK, CommandMapper.commandFor("Enter"))
        assertNull(CommandMapper.commandFor("Keystone"))
        assertNull(CommandMapper.commandFor("Volume"))
    }

    @Test
    fun powerIsPreferredOverOn() {
        val entries = listOf(
            FlipperIrFormat.Entry("On", "parsed", "NEC", 1, 1),
            FlipperIrFormat.Entry("Power", "parsed", "NEC", 1, 2),
        )
        assertEquals("Power", CommandMapper.map(entries)[RemoteCommand.POWER]!!.name)
    }
}
