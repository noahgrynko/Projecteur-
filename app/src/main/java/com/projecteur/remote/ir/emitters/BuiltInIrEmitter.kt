package com.projecteur.remote.ir.emitters

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import com.projecteur.remote.ir.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Émetteur IR intégré via l'API officielle ConsumerIrManager.
 * Le Google Pixel 9a n'en possède pas : [probe] renvoie alors null et l'application l'indique.
 */
class BuiltInIrEmitter private constructor(private val manager: ConsumerIrManager) : IrEmitter {
    override val kind = IrEmitter.Kind.BUILT_IN
    override val name = "Émetteur IR du téléphone"
    override val verification = "ConsumerIrManager.hasIrEmitter() = true"
    override val canReceive = false

    val carrierRanges: List<IntRange> =
        manager.carrierFrequencies?.map { it.minFrequency..it.maxFrequency }.orEmpty()

    override suspend fun transmit(signal: IrSignal) = withContext(Dispatchers.IO) {
        if (carrierRanges.isNotEmpty() && carrierRanges.none { signal.carrierHz in it }) {
            throw IrHardwareException(
                "Fréquence ${signal.carrierHz} Hz non supportée par l'émetteur intégré (plages : $carrierRanges)"
            )
        }
        try {
            manager.transmit(signal.carrierHz, signal.pattern)
        } catch (e: Exception) {
            throw IrHardwareException("L'émetteur IR intégré a refusé le signal : ${e.message}", e)
        }
    }

    override suspend fun receive(timeoutMs: Long): IntArray? =
        throw IrHardwareException("L'émetteur IR intégré d'Android ne sait pas recevoir : un récepteur IR externe est nécessaire.")

    override fun close() = Unit

    companion object {
        data class Probe(val featureDeclared: Boolean, val hasEmitter: Boolean, val emitter: BuiltInIrEmitter?)

        fun probe(context: Context): Probe {
            val feature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)
            val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            val has = runCatching { manager?.hasIrEmitter() == true }.getOrDefault(false)
            return Probe(feature, has, if (has && manager != null) BuiltInIrEmitter(manager) else null)
        }
    }
}
