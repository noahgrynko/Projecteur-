package com.projecteur.remote.core

import kotlinx.coroutines.flow.StateFlow

/** Méthode de communication réellement utilisée avec le projecteur. */
enum class ControlMethod(val label: String) {
    INFRARED("🔴 Infrarouge"),
    BLUETOOTH_HID("🔵 Bluetooth (télécommande HID)"),
    PJLINK("🌐 Réseau local (PJLink)"),
}

/**
 * État de connexion. [Connected] n'est utilisé que lorsqu'un échange bidirectionnel a
 * réellement abouti (poignée de main PJLink, connexion HID confirmée par la pile Bluetooth).
 * L'infrarouge est unidirectionnel : il ne peut jamais être « connecté », seulement « prêt ».
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val detail: String) : ConnectionState()
    data class Connected(val detail: String) : ConnectionState()
    /** Émetteur prêt, mais sans retour possible du projecteur (IR). */
    data class ReadyOneWay(val detail: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

sealed class SendResult {
    data class Sent(val detail: String? = null) : SendResult()
    data class Failed(val message: String) : SendResult()
    data object Unsupported : SendResult()
}

/** Un canal capable d'envoyer des commandes de télécommande au projecteur. */
interface ControlTransport {
    val method: ControlMethod
    val title: String
    val state: StateFlow<ConnectionState>
    val supportedCommands: StateFlow<Set<RemoteCommand>>
    suspend fun send(command: RemoteCommand): SendResult
    fun close()
}
