package com.projecteur.remote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.SocketFactory

/**
 * Client PJLink : une connexion TCP par série de commandes (le projecteur ferme la session après
 * 30 s d'inactivité). L'authentification utilise le mot de passe fourni par l'utilisateur,
 * conformément à la norme ; aucune tentative de deviner un mot de passe.
 */
class PjLinkClient(
    val host: Inet4Address,
    private val password: String?,
    private val socketFactory: SocketFactory,
) {
    class PjLinkException(message: String) : Exception(message)

    private val lock = Mutex()

    /** Exécute plusieurs requêtes dans une même session. Chaque paire = (classe+corps, paramètre). */
    suspend fun <T> session(block: Session.() -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            val socket = socketFactory.createSocket()
            try {
                try {
                    socket.connect(InetSocketAddress(host, PjLink.PORT), 3000)
                } catch (e: SocketTimeoutException) {
                    throw PjLinkException("Appareil non joignable (${host.hostAddress}) : pas de réponse. Projecteur éteint, câble/réseau absent ou LAN désactivé en veille ?")
                } catch (e: ConnectException) {
                    throw PjLinkException("Connexion refusée par ${host.hostAddress}:4352 : PJLink désactivé ou protocole non supporté par cet appareil.")
                } catch (e: NoRouteToHostException) {
                    throw PjLinkException("Aucune route vers ${host.hostAddress} : appareil non joignable sur ce réseau.")
                }
                socket.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
                val out = socket.getOutputStream()
                val greeting = readCr(reader) ?: throw PjLinkException("Aucune bannière PJLink reçue : protocole non supporté.")
                val prefix = when (val g = PjLink.parseGreeting(greeting)) {
                    PjLink.Greeting.NoAuth -> ""
                    is PjLink.Greeting.Auth -> {
                        if (password.isNullOrEmpty()) throw PjLinkException(
                            "Ce projecteur exige le mot de passe PJLink (défini dans son menu Réseau). Saisissez-le dans la configuration."
                        )
                        PjLink.authDigest(g.random, password)
                    }
                    is PjLink.Greeting.Invalid -> throw PjLinkException("Réponse non PJLink (« ${g.line.take(40)} ») : protocole non supporté.")
                }
                Session(reader, out, prefix).block()
            } catch (e: SocketTimeoutException) {
                throw PjLinkException("Le projecteur ne répond plus (délai dépassé).")
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    inner class Session internal constructor(
        private val reader: BufferedReader,
        private val out: OutputStream,
        private var authPrefix: String,
    ) {
        fun request(cls: Int, body: String, param: String): PjLink.Reply {
            out.write((authPrefix + PjLink.command(cls, body, param)).toByteArray(Charsets.US_ASCII))
            out.flush()
            authPrefix = "" // le préfixe n'est envoyé qu'avec la première commande
            val line = readCr(reader) ?: throw PjLinkException("Connexion fermée par le projecteur.")
            val reply = PjLink.parseReply(line, body)
            if (reply is PjLink.Reply.Error && reply.code == "ERRA") throw PjLinkException(reply.message)
            return reply
        }

        fun query(cls: Int, body: String): String? = (request(cls, body, "?") as? PjLink.Reply.Ok)?.value
    }

    private fun readCr(reader: BufferedReader): String? {
        val sb = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\r'.code) return sb.toString()
            if (c != '\n'.code) sb.append(c.toChar())
            if (sb.length > 512) return sb.toString()
        }
    }

    data class Info(
        val name: String?, val manufacturer: String?, val product: String?, val other: String?,
        val pjlinkClass: String?, val power: String?, val inputs: List<String>, val currentInput: String?,
        val lamp: String?, val errors: String?,
    )

    suspend fun readInfo(): Info = session {
        val cls = query(1, "CLSS")
        Info(
            name = query(1, "NAME"),
            manufacturer = query(1, "INF1"),
            product = query(1, "INF2"),
            other = query(1, "INFO"),
            pjlinkClass = cls,
            power = query(1, "POWR"),
            inputs = query(1, "INST")?.let(PjLink::parseInputs).orEmpty(),
            currentInput = query(1, "INPT"),
            lamp = query(1, "LAMP"),
            errors = query(1, "ERST"),
        )
    }
}
