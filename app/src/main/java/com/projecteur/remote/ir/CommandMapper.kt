package com.projecteur.remote.ir

import com.projecteur.remote.core.RemoteCommand

/**
 * Associe les noms de touches trouvés dans les fichiers de télécommande (« Vol_up », « VOL+ »,
 * « Source », « A/V Mute »…) aux commandes de l'application. La correspondance est exacte sur un
 * nom normalisé : si aucun nom connu n'existe dans un profil, la touche est désactivée plutôt que
 * d'envoyer un code au hasard.
 */
object CommandMapper {

    private val synonyms: Map<RemoteCommand, List<String>> = mapOf(
        RemoteCommand.POWER to listOf("power", "pwr", "powertoggle", "poweronoff", "onoff", "standby", "power1", "on"),
        RemoteCommand.VOL_UP to listOf("volup", "vol+", "volume+", "volumeup", "volumep", "volplus", "volumeplus", "volinc"),
        RemoteCommand.VOL_DOWN to listOf("voldn", "voldown", "vol-", "volume-", "volumedown", "volumedn", "volumem", "volminus", "volumeminus", "voldec"),
        RemoteCommand.MUTE to listOf("mute", "audiomute", "soundmute", "volmute", "volumemute"),
        RemoteCommand.SOURCE to listOf("source", "input", "sourcesearch", "src", "inputs", "inputselect", "search"),
        RemoteCommand.HDMI to listOf("hdmi", "hdmi1", "hdmi/video"),
        RemoteCommand.VGA to listOf("vga", "computer", "computer1", "comp", "comp1", "pc", "rgb", "rgb1", "vga1"),
        RemoteCommand.MENU to listOf("menu", "setup"),
        RemoteCommand.UP to listOf("up", "cursorup", "arrowup", "navup"),
        RemoteCommand.DOWN to listOf("down", "dn", "cursordown", "arrowdown", "navdown"),
        RemoteCommand.LEFT to listOf("left", "cursorleft", "arrowleft", "navleft"),
        RemoteCommand.RIGHT to listOf("right", "cursorright", "arrowright", "navright"),
        RemoteCommand.OK to listOf("ok", "enter", "select", "confirm"),
        RemoteCommand.BACK to listOf("back", "return", "previous"),
        RemoteCommand.EXIT to listOf("exit", "esc", "escape", "cancel"),
        RemoteCommand.FREEZE to listOf("freeze", "still", "pause/freeze"),
        RemoteCommand.BLANK to listOf("blank", "avmute", "a/vmute", "picmute", "picturemute", "videomute", "hide", "shutter", "blankscreen", "nopicture"),
    )

    /** Minuscules, sans espaces ni « _ » ni « . » ; « + », « - » et « / » sont conservés. */
    fun normalize(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() || it == '+' || it == '-' || it == '/' }

    fun commandFor(name: String): RemoteCommand? {
        val n = normalize(name)
        return synonyms.entries.firstOrNull { (_, names) -> n in names }?.key
    }

    /**
     * Construit la table commande → entrée. Pour chaque commande, l'ordre des synonymes donne la
     * priorité (ex. « Power » est préféré à « On »). Seules les entrées émettables sont retenues.
     */
    fun map(entries: List<FlipperIrFormat.Entry>): Map<RemoteCommand, FlipperIrFormat.Entry> {
        val usable = entries.filter { it.isSupported }
        val result = LinkedHashMap<RemoteCommand, FlipperIrFormat.Entry>()
        for ((command, names) in synonyms) {
            for (n in names) {
                val hit = usable.firstOrNull { normalize(it.name) == n } ?: continue
                result[command] = hit
                break
            }
        }
        return result
    }
}
