package com.projecteur.remote.ir

import com.projecteur.remote.core.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledDatabaseTest {

    @Test
    fun everyBundledFileParsesWithoutWarnings() {
        val files = BundledDb.allFiles()
        assertEquals(126, files.size)
        files.forEach { f ->
            val r = FlipperIrFormat.parse(f.readText())
            assertTrue("${f.name} : ${r.warnings}", r.warnings.isEmpty())
            assertTrue("${f.name} vide", r.entries.isNotEmpty())
        }
    }

    @Test
    fun everySupportedEntryEncodesAndDecodesBack() {
        var checked = 0
        BundledDb.profiles().flatMap { it.entries }.filter { it.isSupported }.forEach { e ->
            val signal = e.toSignal()
            if (!e.isRaw) {
                val d = IrDecoder.decode(signal.pattern)
                assertNotNull("${e.name} ${e.describe()}", d)
                // NEC dont l'adresse n'est pas « normale » peut ressortir en NECext équivalent.
                val expected = IrProtocols.encode(d!!.protocol, d.address, d.command)
                assertEquals("${e.name} ${e.describe()}", signal, expected)
                checked++
            }
        }
        assertTrue(checked > 1500)
    }

    @Test
    fun onlyKaseikyoIsUnsupported() {
        val unsupported = BundledDb.profiles().flatMap { it.entries }.filterNot { it.isSupported }
        assertEquals(setOf("Kaseikyo"), unsupported.map { it.protocol }.toSet())
    }

    @Test
    fun mostProfilesExposePowerAndNavigation() {
        val profiles = BundledDb.profiles()
        val withPower = profiles.count { RemoteCommand.POWER in it.commands }
        assertTrue("profils avec Power : $withPower", withPower >= 100)
        val withNav = profiles.count { it.commands.keys.containsAll(listOf(RemoteCommand.UP, RemoteCommand.DOWN, RemoteCommand.LEFT, RemoteCommand.RIGHT)) }
        assertTrue("profils avec flèches : $withNav", withNav >= 60)
    }

    @Test
    fun brandAndModelAreDerivedFromPath() {
        val p = IrProfileRepository.profileFromPath("Epson/Epson_EB-450.ir", "Filetype: IR signals file\nVersion: 1\n", IrProfile.Source.BUNDLED)
        assertEquals("Epson", p.brand)
        assertEquals("EB-450", p.model)
        val u = IrProfileRepository.profileFromPath("BrandUnknown/x.ir", "", IrProfile.Source.BUNDLED)
        assertEquals("Marque inconnue", u.brand)
    }

    @Test
    fun autoSelectionRequiresUnambiguousMatch() {
        val profiles = BundledDb.profiles()
        val m = ProfileSearch.bestMatch(profiles, "EPSON", "EB-X12")
        assertNotNull(m)
        assertEquals("Epson/Epson-EB-X12.ir", m!!.id)
        assertNull("marque seule : ambigu", ProfileSearch.bestMatch(profiles, "Epson", null))
        assertNull("marque inconnue", ProfileSearch.bestMatch(profiles, "MarqueInexistante", "X1"))
        assertTrue(ProfileSearch.search(profiles, "benq").isNotEmpty())
    }
}
