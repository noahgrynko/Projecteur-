package com.projecteur.remote.ir.emitters

import com.projecteur.remote.ir.IrSignal

/** Matériel capable d'émettre (et éventuellement de recevoir) des signaux infrarouges. */
interface IrEmitter {
    val kind: Kind
    val name: String
    /** Comment la présence du matériel a été vérifiée (affiché à l'utilisateur). */
    val verification: String
    val canReceive: Boolean

    /** Émet le signal. Lève une [IrHardwareException] avec un message clair en cas d'échec. */
    suspend fun transmit(signal: IrSignal)

    /**
     * Attend un signal IR pendant [timeoutMs] et renvoie les durées brutes (µs, impulsion en
     * premier), ou null si rien n'a été reçu. Lève une exception si le matériel n'a pas de récepteur.
     */
    suspend fun receive(timeoutMs: Long): IntArray?

    fun close()

    enum class Kind(val label: String) {
        BUILT_IN("Émetteur IR intégré au téléphone"),
        USB_IR_TOY("USB IR Toy v2 / Irdroid USB IR Transceiver"),
        USB_BRIDGE("Pont IR USB à firmware ouvert « PROJIR »"),
        AUDIO("Émetteur IR audio (LED sur prise jack)"),
    }
}

class IrHardwareException(message: String, cause: Throwable? = null) : Exception(message, cause)
