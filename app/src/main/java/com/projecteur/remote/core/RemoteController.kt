package com.projecteur.remote.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Garde la méthode de contrôle actuellement sélectionnée par l'utilisateur. */
class RemoteController {
    private val _active = MutableStateFlow<ControlTransport?>(null)
    val active: StateFlow<ControlTransport?> = _active.asStateFlow()

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage.asStateFlow()

    fun activate(transport: ControlTransport) {
        val previous = _active.value
        if (previous !== transport) previous?.close()
        _active.value = transport
        _lastMessage.value = null
    }

    fun deactivate() {
        _active.value?.close()
        _active.value = null
    }

    suspend fun send(command: RemoteCommand): SendResult {
        val transport = _active.value
            ?: return SendResult.Failed(
                "Aucune méthode de contrôle active. Utilisez « Rechercher le vidéoprojecteur » ou la configuration."
            ).also { _lastMessage.value = it.message }
        val result = try {
            transport.send(command)
        } catch (e: Exception) {
            SendResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        _lastMessage.value = when (result) {
            is SendResult.Sent -> "${command.label} : envoyé${result.detail?.let { " — $it" } ?: ""}"
            is SendResult.Failed -> "${command.label} : échec — ${result.message}"
            SendResult.Unsupported -> "${command.label} : commande non disponible avec ${transport.method.label}"
        }
        return result
    }
}
