package com.projecteur.remote.network

import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Diagnostic de joignabilité d'UNE adresse locale autorisée, avec 1, 3, 5 ou 10 requêtes
 * espacées d'une seconde. Deux méthodes :
 *  - ICMP écho (InetAddress.isReachable, sockets « ping » non privilégiés d'Android) ;
 *  - ouverture de connexion TCP sur un port (ex. 4352 PJLink) — une réponse « refusée »
 *    prouve aussi que l'appareil est joignable.
 * Les temps sont mesurés côté application (arrondis à la milliseconde).
 */
class PingDiagnostic(private val cm: ConnectivityManager) {

    enum class Mode(val label: String) { ICMP("ICMP (ping)"), TCP("TCP (connexion)") }

    data class Report(
        val sent: Int, val received: Int, val timesMs: List<Double>, val lines: List<String>,
    ) {
        val lossPercent get() = if (sent == 0) 0.0 else (sent - received) * 100.0 / sent
        val min get() = timesMs.minOrNull()
        val avg get() = if (timesMs.isEmpty()) null else timesMs.average()
        val max get() = timesMs.maxOrNull()
    }

    suspend fun run(
        target: Inet4Address,
        net: NetworkInspector.LocalNetwork,
        count: Int,
        mode: Mode,
        tcpPort: Int,
        onProgress: (String) -> Unit,
    ): Report = withContext(Dispatchers.IO) {
        require(count in listOf(1, 3, 5, 10)) { "Nombre de requêtes non autorisé" }
        val times = ArrayList<Double>()
        val lines = ArrayList<String>()
        val bound = net.network?.let { cm.bindProcessToNetwork(it) } ?: false
        try {
            for (i in 1..count) {
                val start = System.nanoTime()
                val ok: Boolean
                var note = ""
                when (mode) {
                    Mode.ICMP -> ok = runCatching { target.isReachable(2000) }.getOrDefault(false)
                    Mode.TCP -> {
                        val socket = net.network?.socketFactory?.createSocket() ?: Socket()
                        ok = try {
                            socket.connect(InetSocketAddress(target, tcpPort), 2000); note = " (port ouvert)"; true
                        } catch (e: java.net.ConnectException) {
                            note = " (port fermé, mais l'appareil a répondu)"; true
                        } catch (e: Exception) { false } finally { runCatching { socket.close() } }
                    }
                }
                val ms = (System.nanoTime() - start) / 1_000_000.0
                val line = if (ok) {
                    times += ms
                    "Requête $i : réponse de ${target.hostAddress} en %.1f ms$note".format(ms)
                } else "Requête $i : pas de réponse (délai 2 s)"
                lines += line
                onProgress(line)
                if (i < count) delay(1000)
            }
        } finally {
            if (bound) cm.bindProcessToNetwork(null)
        }
        Report(count, times.size, times, lines)
    }
}
