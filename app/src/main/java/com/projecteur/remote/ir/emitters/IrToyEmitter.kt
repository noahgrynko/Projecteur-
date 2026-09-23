package com.projecteur.remote.ir.emitters

import com.projecteur.remote.ir.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Pilote du Dangerous Prototypes USB IR Toy v2 et des appareils utilisant le même firmware
 * (ex. Irdroid USB IR Transceiver). Protocole public « sampling mode » :
 * http://dangerousprototypes.com/docs/USB_IR_Toy:_Sampling_mode
 *
 *  - 0x00 ×5 : remise à zéro ; « S » : passage en mode échantillonnage, réponse « S01 ».
 *  - 0x06 PR2 0x00 : réglage de la porteuse, PR2 = 48 MHz / (16 × f) − 1.
 *  - 0x26 / 0x25 / 0x24 : active l'accusé de réception par bloc, la notification de fin
 *    (« C » succès / « F » échec) et le compte d'octets émis (« t » + 2 octets).
 *  - 0x03 : émission ; durées sur 16 bits gros-boutistes en unités de 21,333 µs,
 *    impulsion en premier, terminées par 0xFFFF, par blocs de 62 octets au plus.
 *  - En réception, le Toy envoie spontanément les durées mesurées au même format, puis 0xFFFF.
 */
class IrToyEmitter(private val link: SerialLink, override val name: String) : IrEmitter {
    override val kind = IrEmitter.Kind.USB_IR_TOY
    override val canReceive = true
    override var verification = "non vérifié"
        private set

    private val lock = Mutex()

    /** Vérifie que le périphérique répond au protocole IR Toy. */
    suspend fun handshake() = withContext(Dispatchers.IO) {
        lock.withLock {
            link.write(ByteArray(5))
            Thread.sleep(50)
            link.drain()
            link.write(byteArrayOf('S'.code.toByte()))
            val reply = String(link.readExact(3, 1500), Charsets.US_ASCII)
            if (!reply.startsWith("S")) {
                throw IrHardwareException("Réponse inattendue « $reply » : ce périphérique n'est pas un IR Toy compatible")
            }
            verification = "Réponse du protocole IR Toy : « $reply »"
        }
    }

    override suspend fun transmit(signal: IrSignal) = withContext(Dispatchers.IO) {
        lock.withLock {
            link.drain(20)
            val pr2 = (48_000_000.0 / (16.0 * signal.carrierHz) - 1).roundToInt().coerceIn(0, 255)
            link.write(byteArrayOf(0x06, pr2.toByte(), 0x00))
            link.write(byteArrayOf(0x26, 0x25, 0x24))
            link.write(byteArrayOf(0x03))
            expectHandshake()

            val data = encode(signal.pattern)
            var offset = 0
            while (offset < data.size) {
                val end = minOf(offset + BLOCK, data.size)
                link.write(data.copyOfRange(offset, end))
                offset = end
                if (offset < data.size) expectHandshake()
            }
            // Attente de la fin : « t » + 2 octets (compte), puis « C » ou « F ».
            val deadline = System.currentTimeMillis() + 3000 + signal.totalDurationUs / 1000
            val seen = ArrayList<Byte>()
            while (System.currentTimeMillis() < deadline) {
                seen += link.readSome(100).toList()
                val t = seen.indexOf('t'.code.toByte())
                if (t >= 0 && seen.size >= t + 4) {
                    val count = ((seen[t + 1].toInt() and 0xFF) shl 8) or (seen[t + 2].toInt() and 0xFF)
                    when (seen[t + 3].toInt().toChar()) {
                        // Selon la version du firmware, le compte inclut ou non le terminateur 0xFFFF.
                        'C' -> if (count >= data.size - 2) return@withLock else throw IrHardwareException(
                            "IR Toy : ${count} octets émis sur ${data.size}"
                        )
                        'F' -> throw IrHardwareException("IR Toy : échec de l'émission (dépassement de tampon)")
                    }
                }
            }
            throw IrHardwareException("IR Toy : pas de confirmation de fin d'émission")
        }
    }

    private fun expectHandshake() {
        val b = link.readExact(1, 1000)[0].toInt() and 0xFF
        if (b != BLOCK) throw IrHardwareException("IR Toy : accusé de réception inattendu ($b)")
    }

    override suspend fun receive(timeoutMs: Long): IntArray? = withContext(Dispatchers.IO) {
        lock.withLock {
            link.drain(20)
            val values = ArrayList<Int>()
            val deadline = System.currentTimeMillis() + timeoutMs
            var pendingByte: Int? = null
            while (System.currentTimeMillis() < deadline) {
                val chunk = link.readSome(100)
                for (b in chunk) {
                    val v = b.toInt() and 0xFF
                    val hi = pendingByte
                    if (hi == null) { pendingByte = v; continue }
                    pendingByte = null
                    val units = (hi shl 8) or v
                    if (units == 0xFFFF) {
                        if (values.isNotEmpty()) return@withLock values.toIntArray()
                    } else {
                        values += (units * UNIT_US).roundToInt().coerceAtLeast(1)
                    }
                }
            }
            if (values.isEmpty()) null else values.toIntArray()
        }
    }

    override fun close() {
        runCatching { link.write(ByteArray(5)) }
        link.close()
    }

    companion object {
        const val VENDOR_ID = 0x04D8
        const val PRODUCT_ID = 0xFD08
        const val UNIT_US = 21.3333
        const val BLOCK = 62

        fun encode(pattern: IntArray): ByteArray {
            val out = ByteArray(pattern.size * 2 + 2)
            pattern.forEachIndexed { i, us ->
                val units = (us / UNIT_US).roundToInt().coerceIn(1, 0xFFFE)
                out[i * 2] = (units shr 8).toByte()
                out[i * 2 + 1] = units.toByte()
            }
            out[out.size - 2] = 0xFF.toByte()
            out[out.size - 1] = 0xFF.toByte()
            return out
        }
    }
}
