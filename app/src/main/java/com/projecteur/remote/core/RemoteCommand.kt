package com.projecteur.remote.core

/**
 * Les commandes d'une télécommande de vidéoprojecteur.
 * Aucune commande ne concerne la projection de l'écran du téléphone : l'application
 * n'envoie que des ordres de télécommande.
 */
enum class RemoteCommand(val label: String) {
    POWER("Power"),
    VOL_UP("Volume +"),
    VOL_DOWN("Volume −"),
    MUTE("Mute"),
    SOURCE("Source"),
    HDMI("HDMI"),
    VGA("VGA / Ordinateur"),
    MENU("Menu"),
    UP("Haut"),
    DOWN("Bas"),
    LEFT("Gauche"),
    RIGHT("Droite"),
    OK("OK"),
    BACK("Retour"),
    EXIT("Exit"),
    FREEZE("Freeze"),
    BLANK("Blank"),
}
