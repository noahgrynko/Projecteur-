package com.projecteur.remote.ir

import kotlin.math.abs

/**
 * Décodeur utilisé par l'apprentissage IR : essaie de reconnaître un protocole connu dans une
 * capture brute. Si rien n'est reconnu, le signal est conservé tel quel (brut) ; aucun protocole
 * n'est jamais deviné au hasard.
 */
object IrDecoder {

    data class Decoded(val protocol: String, val address: Long, val command: Long)

    private fun near(value: Int, target: Int, tolerance: Double = 0.30) =
        abs(value - target) <= target * tolerance

    fun decode(pattern: IntArray): Decoded? =
        decodeNecFamily(pattern) ?: decodeSirc(pattern)

    private fun decodeNecFamily(p: IntArray): Decoded? {
        if (p.size < 67) return null
        val samsung = near(p[0], IrProtocols.SAMSUNG_HDR_MARK) && near(p[1], IrProtocols.SAMSUNG_HDR_SPACE)
        val nec = near(p[0], IrProtocols.NEC_HDR_MARK) && near(p[1], IrProtocols.NEC_HDR_SPACE)
        if (!samsung && !nec) return null
        val bytes = IntArray(4)
        for (bit in 0 until 32) {
            val mark = p[2 + bit * 2]
            val space = p[3 + bit * 2]
            if (!near(mark, IrProtocols.NEC_BIT_MARK, 0.45)) return null
            val one = when {
                near(space, IrProtocols.NEC_ONE_SPACE, 0.35) -> true
                near(space, IrProtocols.NEC_ZERO_SPACE, 0.45) -> false
                else -> return null
            }
            if (one) bytes[bit / 8] = bytes[bit / 8] or (1 shl (bit % 8))
        }
        if (!near(p[66], IrProtocols.NEC_BIT_MARK, 0.45)) return null
        val cmdOk = bytes[2] xor bytes[3] == 0xFF
        return when {
            samsung && bytes[0] == bytes[1] && cmdOk -> Decoded("Samsung32", bytes[0].toLong(), bytes[2].toLong())
            samsung -> null
            bytes[0] xor bytes[1] == 0xFF && cmdOk -> Decoded("NEC", bytes[0].toLong(), bytes[2].toLong())
            else -> Decoded(
                "NECext",
                (bytes[0] or (bytes[1] shl 8)).toLong(),
                (bytes[2] or (bytes[3] shl 8)).toLong(),
            )
        }
    }

    private fun decodeSirc(p: IntArray): Decoded? {
        if (p.size < 25 || !near(p[0], IrProtocols.SIRC_HDR_MARK, 0.25)) return null
        val bits = ArrayList<Boolean>()
        var i = 1
        while (i + 1 < p.size) {
            val space = p[i]
            val mark = p[i + 1]
            if (!near(space, IrProtocols.SIRC_SPACE, 0.5)) break
            bits += when {
                near(mark, IrProtocols.SIRC_ONE_MARK, 0.3) -> true
                near(mark, IrProtocols.SIRC_ZERO_MARK, 0.45) -> false
                else -> return null
            }
            i += 2
        }
        val addressBits = when (bits.size) {
            12 -> 5
            15 -> 8
            20 -> 13
            else -> return null
        }
        var command = 0
        for (b in 0 until 7) if (bits[b]) command = command or (1 shl b)
        var address = 0
        for (b in 0 until addressBits) if (bits[7 + b]) address = address or (1 shl b)
        val protocol = when (addressBits) { 5 -> "SIRC"; 8 -> "SIRC15"; else -> "SIRC20" }
        return Decoded(protocol, address.toLong(), command.toLong())
    }

    /**
     * Nettoie une capture : retire les pauses de début, coupe à la première pause très longue
     * (fin de la touche) et garantit que le motif se termine par une impulsion.
     */
    fun trimCapture(raw: IntArray, maxGapUs: Int = 100_000): IntArray {
        val out = ArrayList<Int>()
        for ((index, v) in raw.withIndex()) {
            val isSpace = index % 2 == 1
            if (isSpace && v >= maxGapUs) break
            out += v
        }
        if (out.size % 2 == 0 && out.isNotEmpty()) out.removeAt(out.size - 1)
        return out.toIntArray()
    }
}
