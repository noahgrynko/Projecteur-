package com.projecteur.remote.ir.emitters

import com.projecteur.remote.ir.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Pont IR USB à firmware ouvert (dossier firmware/projir_bridge) : une carte Arduino/ESP32/RP2040
 * avec une LED IR et, en option, un récepteur TSOP. Protocole texte documenté dans
 * docs/FIRMWARE_PROTOCOL.md :
 *
 *  I\n                      → PROJIR <version> <TX|TX RX> <max_durées>\n
 *  T <kHz> <n> d1 … dn\n    → OK\n | ERR <message>\n
 *  L <timeout_ms>\n         → R <n> d1 … dn\n | TIMEOUT\n
 */
class SerialBridgeEmitter(private val link: SerialLink, override val name: String) : IrEmitter {
    override val kind = IrEmitter.Kind.USB_BRIDGE
    override var canReceive = false
        private set
    override var verification = "non vérifié"
        private set
    private var maxDurations = 256
    private val lock = Mutex()

    suspend fun handshake() = withContext(Dispatchers.IO) {
        lock.withLock {
            // Les cartes avec convertisseur USB-série redémarrent à l'ouverture du port : on insiste.
            val deadline = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < deadline) {
                link.drain(20)
                link.write("I\n".toByteArray())
                val line = link.readLine(700) ?: continue
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.firstOrNull() == "PROJIR") {
                    canReceive = parts.contains("RX")
                    maxDurations = parts.lastOrNull()?.toIntOrNull() ?: maxDurations
                    verification = "Réponse du firmware : « $line »"
                    return@withLock
                }
            }
            throw IrHardwareException(
                "Aucune réponse « PROJIR » : ce périphérique série n'exécute pas le firmware du pont IR"
            )
        }
    }

    override suspend fun transmit(signal: IrSignal) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (signal.pattern.size > maxDurations) {
                throw IrHardwareException("Signal trop long pour ce pont IR (${signal.pattern.size} > $maxDurations durées)")
            }
            link.drain(20)
            val khz = (signal.carrierHz + 500) / 1000
            link.write(("T $khz ${signal.pattern.size} " + signal.pattern.joinToString(" ") + "\n").toByteArray(), 2000)
            val reply = link.readLine(2000 + signal.totalDurationUs / 1000)
                ?: throw IrHardwareException("Pont IR : pas de réponse après émission")
            if (reply.trim() != "OK") throw IrHardwareException("Pont IR : $reply")
        }
    }

    override suspend fun receive(timeoutMs: Long): IntArray? = withContext(Dispatchers.IO) {
        if (!canReceive) throw IrHardwareException("Ce pont IR n'a pas de récepteur IR (firmware sans « RX »).")
        lock.withLock {
            link.drain(20)
            link.write("L $timeoutMs\n".toByteArray())
            val reply = link.readLine(timeoutMs + 2000) ?: return@withLock null
            val parts = reply.trim().split(Regex("\\s+"))
            when (parts.firstOrNull()) {
                "TIMEOUT" -> null
                "R" -> parts.drop(2).map { it.toInt() }.toIntArray().takeIf { it.isNotEmpty() }
                else -> throw IrHardwareException("Pont IR : réponse inattendue « $reply »")
            }
        }
    }

    override fun close() {
        link.close()
    }
}
