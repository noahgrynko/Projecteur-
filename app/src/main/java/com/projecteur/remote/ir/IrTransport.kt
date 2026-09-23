package com.projecteur.remote.ir

import com.projecteur.remote.core.ConnectionState
import com.projecteur.remote.core.ControlMethod
import com.projecteur.remote.core.ControlTransport
import com.projecteur.remote.core.RemoteCommand
import com.projecteur.remote.core.SendResult
import com.projecteur.remote.ir.emitters.IrEmitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Télécommande infrarouge : un émetteur réellement présent + un profil de codes.
 * Les commandes apprises portant le nom d'une touche remplacent celles du profil.
 *
 * L'IR est unidirectionnel : l'état reste « prêt » et n'affiche jamais « connecté », car le
 * projecteur ne renvoie aucune confirmation.
 */
class IrTransport(
    private val emitter: IrEmitter,
    val profile: IrProfile?,
    learned: List<FlipperIrFormat.Entry>,
) : ControlTransport {
    override val method = ControlMethod.INFRARED
    override val title = profile?.displayName ?: "Commandes apprises"

    private val table: Map<RemoteCommand, FlipperIrFormat.Entry> =
        (profile?.commands.orEmpty()) + CommandMapper.map(learned)

    private val _state = MutableStateFlow<ConnectionState>(
        ConnectionState.ReadyOneWay("${emitter.name} — IR unidirectionnel, aucune confirmation possible du projecteur")
    )
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _supported = MutableStateFlow(table.keys)
    override val supportedCommands: StateFlow<Set<RemoteCommand>> = _supported.asStateFlow()

    override suspend fun send(command: RemoteCommand): SendResult {
        val entry = table[command] ?: return SendResult.Unsupported
        return sendEntry(entry)
    }

    suspend fun sendEntry(entry: FlipperIrFormat.Entry): SendResult = try {
        emitter.transmit(entry.toSignal())
        SendResult.Sent("« ${entry.name} » émis (${entry.describe()})")
    } catch (e: Exception) {
        SendResult.Failed(e.message ?: "erreur d'émission IR")
    }

    override fun close() = Unit
}
