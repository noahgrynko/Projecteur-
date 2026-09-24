package com.projecteur.remote.ir.emitters

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import com.projecteur.remote.ir.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Émetteur IR « audio » : deux LED IR montées tête-bêche entre les voies gauche et droite d'une
 * prise jack 3,5 mm (dongle vendu tel quel, ou montage maison), branchée via un adaptateur
 * USB-C → jack.
 *
 * Chaque voie reçoit une sinusoïde à f/2 en opposition de phase : les LED s'allument
 * alternativement à chaque demi-période, ce qui produit une porteuse à f (ex. 38 kHz).
 *
 * LIMITE IMPORTANTE : Android voit seulement une sortie audio ; il est impossible de vérifier
 * électroniquement qu'une LED IR y est branchée. Ce mode doit être activé explicitement par
 * l'utilisateur et validé par un essai réel sur le projecteur. Volume média au maximum requis.
 */
class AudioIrEmitter(private val device: AudioDeviceInfo) : IrEmitter {
    override val kind = IrEmitter.Kind.AUDIO
    override val name = "Sortie audio « ${device.productName} »"
    override val verification = "Sortie audio présente ; présence de la LED IR NON vérifiable (activé manuellement)"
    override val canReceive = false

    private val sampleRate: Int = device.sampleRates.filter { it >= 44_100 }.maxOrNull()?.coerceAtMost(96_000) ?: 48_000

    override suspend fun transmit(signal: IrSignal) = withContext(Dispatchers.Default) {
        val toneHz = signal.carrierHz / 2.0
        if (toneHz >= sampleRate / 2.0) {
            throw IrHardwareException(
                "Porteuse ${signal.carrierHz} Hz impossible à produire à $sampleRate Hz d'échantillonnage"
            )
        }
        val pcm = synthesize(signal, sampleRate)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.setPreferredDevice(device)
            track.setVolume(AudioTrack.getMaxVolume())
            track.play()
            delay(pcm.size / 2 * 1000L / sampleRate + 150)
        } catch (e: Exception) {
            throw IrHardwareException("Échec de la sortie audio : ${e.message}", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    override suspend fun receive(timeoutMs: Long): IntArray? =
        throw IrHardwareException("Un émetteur IR audio ne peut pas recevoir : un récepteur IR USB est nécessaire.")

    override fun close() = Unit

    companion object {
        val SUPPORTED_TYPES = setOf(
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
        )

        /** PCM stéréo entrelacé : gauche = +sin, droite = −sin pendant les impulsions, silence sinon. */
        fun synthesize(signal: IrSignal, sampleRate: Int): ShortArray {
            val toneHz = signal.carrierHz / 2.0
            val lead = sampleRate / 100 // 10 ms de silence pour laisser la sortie s'ouvrir
            val totalSamples = lead + (signal.totalDurationUs * sampleRate / 1_000_000L).toInt() + lead
            val out = ShortArray(totalSamples * 2)
            var elapsedUs = 0L
            signal.pattern.forEachIndexed { i, durationUs ->
                val start = lead + (elapsedUs * sampleRate / 1_000_000L).toInt()
                elapsedUs += durationUs
                val end = lead + (elapsedUs * sampleRate / 1_000_000L).toInt()
                if (i % 2 == 0) {
                    for (s in start until end) {
                        val v = (sin(2 * PI * toneHz * s / sampleRate) * Short.MAX_VALUE).toInt().toShort()
                        out[s * 2] = v
                        out[s * 2 + 1] = (-v).toShort()
                    }
                }
            }
            return out
        }
    }
}
