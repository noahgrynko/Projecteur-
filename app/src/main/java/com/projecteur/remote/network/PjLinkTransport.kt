package com.projecteur.remote.network

import com.projecteur.remote.core.ConnectionState
import com.projecteur.remote.core.ControlMethod
import com.projecteur.remote.core.ControlTransport
import com.projecteur.remote.core.RemoteCommand
import com.projecteur.remote.core.SendResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Télécommande par PJLink. Commandes disponibles selon la norme :
 *  - classe 1 : Power (POWR), Source/HDMI/VGA (INPT), Mute audio et Blank (AVMT) ;
 *  - classe 2 : Volume +/− (SVOL), Freeze (FREZ).
 * Menu, flèches, OK, Retour et Exit n'existent pas dans PJLink : ces touches sont désactivées.
 */
class PjLinkTransport(private val client: PjLinkClient) : ControlTransport {
    override val method = ControlMethod.PJLINK
    override var title = "PJLink ${client.host.hostAddress}"
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _supported = MutableStateFlow<Set<RemoteCommand>>(emptySet())
    override val supportedCommands: StateFlow<Set<RemoteCommand>> = _supported.asStateFlow()

    var info: PjLinkClient.Info? = null
        private set

    /** Établit réellement la session (bannière, authentification, lecture d'informations). */
    suspend fun connect(): Result<PjLinkClient.Info> {
        _state.value = ConnectionState.Connecting("Connexion PJLink à ${client.host.hostAddress}…")
        return runCatching { client.readInfo() }.onSuccess { i ->
            info = i
            title = listOfNotNull(i.manufacturer, i.product).joinToString(" ").ifBlank { i.name ?: title }
            val class2 = i.pjlinkClass == "2"
            _supported.value = buildSet {
                add(RemoteCommand.POWER)
                add(RemoteCommand.MUTE)
                add(RemoteCommand.BLANK)
                if (i.inputs.isNotEmpty()) add(RemoteCommand.SOURCE)
                if (i.inputs.any { it.startsWith("3") }) add(RemoteCommand.HDMI)
                if (i.inputs.any { it.startsWith("1") }) add(RemoteCommand.VGA)
                if (class2) { add(RemoteCommand.VOL_UP); add(RemoteCommand.VOL_DOWN); add(RemoteCommand.FREEZE) }
            }
            _state.value = ConnectionState.Connected(
                "PJLink classe ${i.pjlinkClass ?: "?"} — ${PjLink.powerLabel(i.power ?: "?")}"
            )
        }.onFailure { e ->
            _state.value = ConnectionState.Error(e.message ?: "Échec PJLink")
        }
    }

    override suspend fun send(command: RemoteCommand): SendResult {
        if (command !in _supported.value) return SendResult.Unsupported
        return try {
            val detail = client.session {
                when (command) {
                    RemoteCommand.POWER -> {
                        val power = query(1, "POWR")
                        when (power) {
                            "1" -> expectOk(request(1, "POWR", "0"), "Extinction demandée")
                            "0" -> expectOk(request(1, "POWR", "1"), "Allumage demandé")
                            "2", "3" -> throw PjLinkClient.PjLinkException("Projecteur en ${PjLink.powerLabel(power).lowercase()} : patientez.")
                            else -> throw PjLinkClient.PjLinkException("État d'alimentation inconnu")
                        }
                    }
                    RemoteCommand.MUTE -> toggleAvMute(this, audio = true)
                    RemoteCommand.BLANK -> toggleAvMute(this, audio = false)
                    RemoteCommand.VOL_UP -> expectOk(request(2, "SVOL", "1"), "Volume +")
                    RemoteCommand.VOL_DOWN -> expectOk(request(2, "SVOL", "0"), "Volume −")
                    RemoteCommand.FREEZE -> {
                        val frozen = query(2, "FREZ") == "1"
                        expectOk(request(2, "FREZ", if (frozen) "0" else "1"), if (frozen) "Image libérée" else "Image figée")
                    }
                    RemoteCommand.SOURCE -> {
                        val inputs = query(1, "INST")?.let(PjLink::parseInputs).orEmpty()
                        if (inputs.isEmpty()) throw PjLinkClient.PjLinkException("Le projecteur ne publie aucune entrée")
                        val current = query(1, "INPT")
                        val next = inputs[(inputs.indexOf(current) + 1).mod(inputs.size)]
                        expectOk(request(1, "INPT", next), "Entrée ${PjLink.inputTypeLabel(next)}")
                    }
                    RemoteCommand.HDMI, RemoteCommand.VGA -> {
                        val prefix = if (command == RemoteCommand.HDMI) "3" else "1"
                        val inputs = info?.inputs.orEmpty().filter { it.startsWith(prefix) }
                        if (inputs.isEmpty()) throw PjLinkClient.PjLinkException("Entrée absente sur ce projecteur")
                        val current = query(1, "INPT")
                        val next = if (current in inputs) inputs[(inputs.indexOf(current) + 1) % inputs.size] else inputs.first()
                        expectOk(request(1, "INPT", next), "Entrée ${PjLink.inputTypeLabel(next)}")
                    }
                    else -> throw IllegalStateException()
                }
            }
            _state.value = ConnectionState.Connected(_state.value.let { (it as? ConnectionState.Connected)?.detail ?: "PJLink" })
            SendResult.Sent(detail)
        } catch (e: PjLinkClient.PjLinkException) {
            _state.value = ConnectionState.Error(e.message ?: "Erreur PJLink")
            SendResult.Failed(e.message ?: "Erreur PJLink")
        } catch (e: java.io.IOException) {
            _state.value = ConnectionState.Error("Appareil non joignable : ${e.message}")
            SendResult.Failed("Appareil non joignable : ${e.message}")
        }
    }

    private fun toggleAvMute(s: PjLinkClient.Session, audio: Boolean): String {
        // AVMT : 1x = image, 2x = son, 3x = les deux ; x = 1 activé, 0 désactivé.
        val status = s.query(1, "AVMT") ?: "30"
        val videoOn = status == "11" || status == "31"
        val audioOn = status == "21" || status == "31"
        return if (audio) {
            val r = s.request(1, "AVMT", if (audioOn) "20" else "21")
            expectOk(r, if (audioOn) "Son rétabli" else "Son coupé")
        } else {
            val r = s.request(1, "AVMT", if (videoOn) "10" else "11")
            expectOk(r, if (videoOn) "Image rétablie" else "Image masquée")
        }
    }

    private fun expectOk(reply: PjLink.Reply, success: String): String = when (reply) {
        is PjLink.Reply.Ok -> if (reply.value == "OK") success else "$success (${reply.value})"
        is PjLink.Reply.Error -> throw PjLinkClient.PjLinkException(reply.message)
    }

    override fun close() {
        _state.value = ConnectionState.Disconnected
    }
}
