package com.projecteur.remote.ir

/**
 * Un signal infrarouge prêt à être émis.
 * [pattern] alterne durées d'impulsion (LED allumée, modulée à [carrierHz]) et de pause,
 * en microsecondes, en commençant par une impulsion — le même format que
 * android.hardware.ConsumerIrManager.transmit().
 */
data class IrSignal(
    val carrierHz: Int,
    val pattern: IntArray,
    val dutyCycle: Float = 0.33f,
) {
    init {
        require(carrierHz in 15_000..100_000) { "Fréquence porteuse invalide : $carrierHz Hz" }
        require(pattern.isNotEmpty()) { "Signal IR vide" }
        require(pattern.all { it > 0 }) { "Durées IR invalides (≤ 0)" }
    }

    val totalDurationUs: Long get() = pattern.fold(0L) { acc, v -> acc + v }

    override fun equals(other: Any?): Boolean =
        other is IrSignal && carrierHz == other.carrierHz && pattern.contentEquals(other.pattern) &&
            dutyCycle == other.dutyCycle

    override fun hashCode(): Int = 31 * (31 * carrierHz + pattern.contentHashCode()) + dutyCycle.hashCode()
}
