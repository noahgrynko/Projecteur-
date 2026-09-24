package com.projecteur.remote.bluetooth

import java.util.UUID

/**
 * Interprétation honnête des services Bluetooth annoncés par un appareil.
 * Il n'existe AUCUN profil Bluetooth standard de « contrôle de vidéoprojecteur ». Les services
 * audio (A2DP) servent au son uniquement ; l'application ne prétend jamais le contraire.
 */
object BluetoothServices {

    enum class Meaning(val controlsProjector: Boolean) {
        AUDIO_ONLY(false), MEDIA_REMOTE(false), INPUT_DEVICE(false), SERIAL_UNDOCUMENTED(false),
        GENERIC(false), OTHER(false),
    }

    data class Service(val uuid: UUID, val name: String, val meaning: Meaning, val explanation: String)

    private fun sig(short: Int): UUID = UUID.fromString("%08x-0000-1000-8000-00805f9b34fb".format(short))

    private val known: Map<UUID, Triple<String, Meaning, String>> = mapOf(
        sig(0x110B) to Triple("A2DP Audio Sink", Meaning.AUDIO_ONLY, "Reçoit du son (enceinte du projecteur). Ne permet pas de le contrôler."),
        sig(0x110A) to Triple("A2DP Audio Source", Meaning.AUDIO_ONLY, "Envoie du son vers un casque/une enceinte. Ne permet pas de le contrôler."),
        sig(0x110D) to Triple("Advanced Audio", Meaning.AUDIO_ONLY, "Transport audio uniquement."),
        sig(0x110C) to Triple("AVRCP Target", Meaning.MEDIA_REMOTE, "Commandes de lecture audio (lecture/pause/volume du flux Bluetooth). Android n'offre pas d'API publique pour s'en servir comme télécommande de projecteur."),
        sig(0x110E) to Triple("AVRCP Controller", Meaning.MEDIA_REMOTE, "L'appareil sait piloter la lecture audio d'un autre appareil ; ce n'est pas un service de contrôle du projecteur."),
        sig(0x110F) to Triple("AVRCP Controller", Meaning.MEDIA_REMOTE, "Voir AVRCP."),
        sig(0x1124) to Triple("HID (périphérique d'entrée)", Meaning.INPUT_DEVICE, "L'appareil est lui-même un clavier/une télécommande."),
        sig(0x1812) to Triple("HID over GATT", Meaning.INPUT_DEVICE, "L'appareil est lui-même un périphérique d'entrée BLE."),
        sig(0x1101) to Triple("Port série (SPP)", Meaning.SERIAL_UNDOCUMENTED, "Liaison série présente, mais aucun protocole de commande documenté par le fabricant n'est intégré : non utilisé."),
        sig(0x1108) to Triple("Headset", Meaning.AUDIO_ONLY, "Profil casque (audio)."),
        sig(0x111E) to Triple("Handsfree", Meaning.AUDIO_ONLY, "Profil mains-libres (audio)."),
        sig(0x1105) to Triple("OBEX Object Push", Meaning.OTHER, "Transfert de fichiers."),
        sig(0x1200) to Triple("PnP Information", Meaning.GENERIC, "Informations d'identification (fabricant/produit)."),
        sig(0x1800) to Triple("Generic Access", Meaning.GENERIC, "Service BLE générique."),
        sig(0x1801) to Triple("Generic Attribute", Meaning.GENERIC, "Service BLE générique."),
        sig(0x180A) to Triple("Device Information", Meaning.GENERIC, "Fabricant/modèle (lecture seule)."),
        sig(0x180F) to Triple("Battery", Meaning.GENERIC, "Niveau de batterie."),
    )

    fun describe(uuid: UUID): Service {
        val k = known[uuid]
        return if (k != null) Service(uuid, k.first, k.second, k.third)
        else Service(uuid, "Service propriétaire ${uuid.toString().take(8)}", Meaning.OTHER,
            "Service non documenté publiquement : aucune commande n'est envoyée.")
    }

    /** Classe d'appareil Bluetooth classique (Class of Device). */
    fun deviceClassLabel(majorMinor: Int?): String? {
        if (majorMinor == null) return null
        val major = majorMinor and 0x1F00
        return when (majorMinor and 0x1FFC) {
            0x043C -> "Audio/Vidéo — écran vidéo et haut-parleur"
            0x0438 -> "Audio/Vidéo — moniteur vidéo"
            0x0414 -> "Audio/Vidéo — haut-parleur"
            0x0404 -> "Audio/Vidéo — oreillette"
            0x0418 -> "Audio/Vidéo — casque audio"
            0x0424 -> "Audio/Vidéo — décodeur"
            0x0428 -> "Audio/Vidéo — chaîne Hi-Fi"
            0x042C -> "Audio/Vidéo — magnétoscope"
            0x0430 -> "Audio/Vidéo — caméra vidéo"
            else -> when (major) {
                0x0100 -> "Ordinateur"
                0x0200 -> "Téléphone"
                0x0400 -> "Audio/Vidéo"
                0x0500 -> "Périphérique (clavier, souris, télécommande)"
                0x0600 -> "Imagerie (affichage/imprimante)"
                else -> null
            }
        }
    }

    private val projectorBrands = listOf(
        "epson", "benq", "optoma", "viewsonic", "xgimi", "nebula", "vankyo", "acer", "infocus",
        "vivitek", "maxell", "hitachi", "panasonic", "sony", "sharp", "casio", "canon", "dangbei",
        "wanbo", "yaber", "groview", "apeman", "philips", "hisense", "samsung freestyle",
    )

    /** Indice (jamais une certitude) : classe « écran vidéo » ou nom évoquant un projecteur. */
    fun looksLikeProjector(name: String?, majorMinor: Int?): Boolean {
        val n = name?.lowercase().orEmpty()
        val cls = majorMinor?.and(0x1FFC)
        if (cls == 0x043C || cls == 0x0438) return true
        if (listOf("projector", "projecteur", "beamer", "proyector").any { n.contains(it) }) return true
        val audioVideo = majorMinor == null || majorMinor and 0x1F00 == 0x0400
        return audioVideo && projectorBrands.any { n.contains(it) }
    }
}
