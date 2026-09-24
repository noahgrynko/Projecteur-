package com.projecteur.remote.network

import java.security.MessageDigest

/**
 * Protocole PJLink (JBMIA), standard ouvert de contrôle des vidéoprojecteurs sur TCP 4352,
 * implémenté par Epson, NEC/Sharp, Panasonic, Sony, Hitachi/Maxell, Canon, BenQ, Optoma, etc.
 * Spécifications : https://pjlink.jbmia.or.jp/english/
 *
 * Fonctions pures (sans réseau) : construction des commandes et analyse des réponses.
 */
object PjLink {
    const val PORT = 4352

    /** Préfixe d'authentification : MD5(aléa + mot de passe), en hexadécimal minuscule. */
    fun authDigest(random: String, password: String): String =
        MessageDigest.getInstance("MD5").digest((random + password).toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }

    sealed class Greeting {
        data object NoAuth : Greeting()
        data class Auth(val random: String) : Greeting()
        data class Invalid(val line: String) : Greeting()
    }

    fun parseGreeting(line: String): Greeting {
        val t = line.trim()
        return when {
            t == "PJLINK 0" -> Greeting.NoAuth
            t.startsWith("PJLINK 1 ") -> Greeting.Auth(t.removePrefix("PJLINK 1 ").trim())
            else -> Greeting.Invalid(t)
        }
    }

    fun command(cls: Int, body: String, param: String) = "%$cls$body $param\r"

    sealed class Reply {
        data class Ok(val value: String) : Reply()
        data class Error(val code: String, val message: String) : Reply()
    }

    /** Analyse « %1POWR=1 », « %1POWR=OK », « %1POWR=ERR3 », « PJLINK ERRA ». */
    fun parseReply(line: String, expectedBody: String): Reply {
        val t = line.trim()
        if (t == "PJLINK ERRA") return Reply.Error("ERRA", "Mot de passe PJLink incorrect")
        val eq = t.indexOf('=')
        if (!t.startsWith("%") || eq < 0 || t.length < 6) return Reply.Error("?", "Réponse illisible : « $t »")
        val body = t.substring(2, 6)
        if (!body.equals(expectedBody, true)) return Reply.Error("?", "Réponse inattendue : « $t »")
        val value = t.substring(eq + 1)
        return when (value) {
            "ERR1" -> Reply.Error(value, "Commande non prise en charge par ce projecteur")
            "ERR2" -> Reply.Error(value, "Paramètre refusé par le projecteur")
            "ERR3" -> Reply.Error(value, "Commande indisponible pour le moment (veille, préchauffage ou refroidissement)")
            "ERR4" -> Reply.Error(value, "Le projecteur signale une panne")
            else -> Reply.Ok(value)
        }
    }

    fun powerLabel(value: String) = when (value) {
        "0" -> "En veille"
        "1" -> "Allumé"
        "2" -> "Refroidissement"
        "3" -> "Préchauffage"
        else -> "Inconnu ($value)"
    }

    /** Entrées « 11 12 31 32 » → types : 1 RVB (VGA), 2 vidéo, 3 numérique (HDMI/DVI), 4 stockage, 5 réseau, 6 interne. */
    fun parseInputs(value: String): List<String> = value.trim().split(Regex("\\s+")).filter { it.length == 2 }

    fun inputTypeLabel(code: String) = when (code.firstOrNull()) {
        '1' -> "RVB/VGA"
        '2' -> "Vidéo"
        '3' -> "Numérique (HDMI)"
        '4' -> "Stockage"
        '5' -> "Réseau"
        '6' -> "Interne"
        else -> "?"
    } + " ${code.drop(1)}"

    /** Réponse à la recherche UDP de classe 2 : « %2ACKN=00:11:22:33:44:55 ». */
    fun parseSearchAck(line: String): String? {
        val t = line.trim()
        return if (t.startsWith("%2ACKN=")) t.removePrefix("%2ACKN=") else null
    }
}
