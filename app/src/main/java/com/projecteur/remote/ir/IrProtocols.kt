package com.projecteur.remote.ir

/**
 * Encodeurs des protocoles IR présents dans la base Flipper-IRDB des vidéoprojecteurs.
 *
 * Conventions reprises du format Flipper Zero : les champs « address » et « command » sont des
 * entiers 32 bits stockés en petit-boutiste (octet de poids faible en premier), et les bits sont
 * émis poids faible en premier.
 *
 * Seuls des protocoles publics et documentés sont implémentés. Un protocole inconnu est refusé
 * (UnsupportedProtocolException) : l'application n'invente jamais de trame.
 */
object IrProtocols {

    class UnsupportedProtocolException(protocol: String) :
        Exception("Protocole IR « $protocol » non pris en charge par l'application")

    val supported = listOf("NEC", "NECext", "Samsung32", "SIRC", "SIRC15", "SIRC20")

    fun encode(protocol: String, address: Long, command: Long): IrSignal = when (protocol) {
        "NEC" -> nec(address.toInt() and 0xFF, command.toInt() and 0xFF)
        "NECext" -> necExt(address.toInt() and 0xFFFF, command.toInt() and 0xFFFF)
        "Samsung32" -> samsung32(address.toInt() and 0xFF, command.toInt() and 0xFF)
        "SIRC" -> sirc(command.toInt(), address.toInt(), addressBits = 5)
        "SIRC15" -> sirc(command.toInt(), address.toInt(), addressBits = 8)
        "SIRC20" -> sirc(command.toInt(), address.toInt(), addressBits = 13)
        else -> throw UnsupportedProtocolException(protocol)
    }

    // --- NEC (porteuse 38 kHz) ---------------------------------------------------------------
    const val NEC_HDR_MARK = 9000
    const val NEC_HDR_SPACE = 4500
    const val NEC_BIT_MARK = 560
    const val NEC_ZERO_SPACE = 560
    const val NEC_ONE_SPACE = 1690

    /** NEC standard : adresse, adresse inversée, commande, commande inversée. */
    fun nec(address: Int, command: Int): IrSignal =
        necFrame(listOf(address, address.inv() and 0xFF, command, command.inv() and 0xFF))

    /** NEC étendu : adresse 16 bits et commande 16 bits, octet de poids faible en premier. */
    fun necExt(address: Int, command: Int): IrSignal =
        necFrame(listOf(address and 0xFF, address shr 8 and 0xFF, command and 0xFF, command shr 8 and 0xFF))

    private fun necFrame(bytes: List<Int>): IrSignal {
        val p = ArrayList<Int>(68)
        p += NEC_HDR_MARK; p += NEC_HDR_SPACE
        pulseDistanceBytes(p, bytes, NEC_BIT_MARK, NEC_ZERO_SPACE, NEC_ONE_SPACE)
        p += NEC_BIT_MARK
        return IrSignal(38_000, p.toIntArray())
    }

    // --- Samsung32 (38 kHz) ------------------------------------------------------------------
    const val SAMSUNG_HDR_MARK = 4500
    const val SAMSUNG_HDR_SPACE = 4500

    fun samsung32(address: Int, command: Int): IrSignal {
        val p = ArrayList<Int>(68)
        p += SAMSUNG_HDR_MARK; p += SAMSUNG_HDR_SPACE
        pulseDistanceBytes(
            p, listOf(address, address, command, command.inv() and 0xFF),
            NEC_BIT_MARK, NEC_ZERO_SPACE, NEC_ONE_SPACE,
        )
        p += NEC_BIT_MARK
        return IrSignal(38_000, p.toIntArray())
    }

    private fun pulseDistanceBytes(p: MutableList<Int>, bytes: List<Int>, mark: Int, zero: Int, one: Int) {
        for (b in bytes) for (i in 0 until 8) {
            p += mark
            p += if ((b shr i) and 1 == 1) one else zero
        }
    }

    // --- Sony SIRC (40 kHz, largeur d'impulsion) ---------------------------------------------
    const val SIRC_HDR_MARK = 2400
    const val SIRC_SPACE = 600
    const val SIRC_ONE_MARK = 1200
    const val SIRC_ZERO_MARK = 600
    const val SIRC_FRAME_PERIOD = 45_000
    /** Les appareils Sony exigent la trame répétée au moins 3 fois. */
    const val SIRC_REPEATS = 3

    fun sirc(command: Int, address: Int, addressBits: Int): IrSignal {
        val frame = ArrayList<Int>()
        frame += SIRC_HDR_MARK
        val bits = ArrayList<Boolean>()
        for (i in 0 until 7) bits += (command shr i) and 1 == 1
        for (i in 0 until addressBits) bits += (address shr i) and 1 == 1
        for (b in bits) {
            frame += SIRC_SPACE
            frame += if (b) SIRC_ONE_MARK else SIRC_ZERO_MARK
        }
        val frameDuration = frame.sum()
        val out = ArrayList<Int>()
        repeat(SIRC_REPEATS) { i ->
            out += frame
            if (i < SIRC_REPEATS - 1) out += SIRC_FRAME_PERIOD - frameDuration
        }
        return IrSignal(40_000, out.toIntArray())
    }
}
