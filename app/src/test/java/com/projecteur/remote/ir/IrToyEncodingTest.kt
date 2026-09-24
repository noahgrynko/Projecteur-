package com.projecteur.remote.ir

import com.projecteur.remote.ir.emitters.IrToyEmitter
import org.junit.Assert.assertEquals
import org.junit.Test

class IrToyEncodingTest {
    @Test
    fun durationsAreBigEndian21usUnitsTerminatedByFFFF() {
        val bytes = IrToyEmitter.encode(intArrayOf(9000, 4500, 560))
        // 9000 / 21.333 = 421.9 → 422 = 0x01A6
        assertEquals(0x01, bytes[0].toInt() and 0xFF)
        assertEquals(0xA6, bytes[1].toInt() and 0xFF)
        // 560 / 21.333 = 26.25 → 26
        assertEquals(26, bytes[5].toInt() and 0xFF)
        assertEquals(0xFF, bytes[6].toInt() and 0xFF)
        assertEquals(0xFF, bytes[7].toInt() and 0xFF)
        assertEquals(8, bytes.size)
    }
}
