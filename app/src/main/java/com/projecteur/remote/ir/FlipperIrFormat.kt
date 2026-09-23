package com.projecteur.remote.ir

/**
 * Lecture/écriture du format texte « IR signals file » du Flipper Zero, utilisé par la base
 * communautaire Flipper-IRDB (licence CC0) intégrée à l'application.
 *
 * Exemple :
 * ```
 * Filetype: IR signals file
 * Version: 1
 * #
 * name: Power
 * type: parsed
 * protocol: NECext
 * address: 83 55 00 00
 * command: 90 6F 00 00
 * ```
 */
object FlipperIrFormat {

    data class Entry(
        val name: String,
        val type: String,
        val protocol: String? = null,
        val address: Long = 0,
        val command: Long = 0,
        val frequency: Int = 38_000,
        val dutyCycle: Float = 0.33f,
        val data: IntArray = IntArray(0),
    ) {
        val isRaw get() = type.equals("raw", ignoreCase = true)

        /** Convertit l'entrée en signal émis. Lève une exception si le protocole est inconnu. */
        fun toSignal(): IrSignal = if (isRaw) {
            IrSignal(frequency, rawPattern(data), dutyCycle)
        } else {
            IrProtocols.encode(protocol ?: throw IllegalStateException("Protocole manquant pour « $name »"), address, command)
        }

        val isSupported: Boolean
            get() = if (isRaw) data.isNotEmpty() else protocol in IrProtocols.supported

        fun describe(): String = if (isRaw) "brut, ${frequency / 1000.0} kHz, ${data.size} durées"
        else "$protocol adr=0x${address.toString(16).uppercase()} cmd=0x${command.toString(16).uppercase()}"

        override fun equals(other: Any?) = other is Entry && name == other.name && type == other.type &&
            protocol == other.protocol && address == other.address && command == other.command &&
            frequency == other.frequency && data.contentEquals(other.data)

        override fun hashCode() = name.hashCode()
    }

    /** Une durée brute ne peut pas être nulle ; un motif doit finir par une impulsion. */
    private fun rawPattern(data: IntArray): IntArray {
        val cleaned = data.map { if (it < 1) 1 else it }.toMutableList()
        if (cleaned.size % 2 == 0 && cleaned.isNotEmpty()) cleaned.removeAt(cleaned.size - 1)
        return cleaned.toIntArray()
    }

    class ParseResult(val entries: List<Entry>, val warnings: List<String>, val comments: List<String>)

    fun parse(text: String): ParseResult {
        val entries = ArrayList<Entry>()
        val warnings = ArrayList<String>()
        val comments = ArrayList<String>()
        var current = LinkedHashMap<String, String>()

        fun flush() {
            if (current.isEmpty()) return
            val name = current["name"]
            if (name == null) { current = LinkedHashMap(); return }
            try {
                entries += toEntry(current)
            } catch (e: Exception) {
                warnings += "« $name » ignoré : ${e.message}"
            }
            current = LinkedHashMap()
        }

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                flush()
                val c = line.removePrefix("#").trim()
                if (c.isNotEmpty()) comments += c
                continue
            }
            val sep = line.indexOf(':')
            if (sep <= 0) continue
            val key = line.substring(0, sep).trim().lowercase()
            val value = line.substring(sep + 1).trim()
            when (key) {
                "filetype", "version" -> Unit
                "name" -> { flush(); current["name"] = value }
                else -> current[key] = value
            }
        }
        flush()
        return ParseResult(entries, warnings, comments)
    }

    private fun toEntry(map: Map<String, String>): Entry {
        val name = map.getValue("name")
        val type = map["type"] ?: throw IllegalArgumentException("champ « type » manquant")
        return when (type.lowercase()) {
            "parsed" -> Entry(
                name = name,
                type = "parsed",
                protocol = map["protocol"] ?: throw IllegalArgumentException("champ « protocol » manquant"),
                address = parseLeHex(map["address"] ?: "00 00 00 00"),
                command = parseLeHex(map["command"] ?: throw IllegalArgumentException("champ « command » manquant")),
            )
            "raw" -> Entry(
                name = name,
                type = "raw",
                frequency = map["frequency"]?.toInt() ?: 38_000,
                dutyCycle = map["duty_cycle"]?.toFloat() ?: 0.33f,
                data = (map["data"] ?: throw IllegalArgumentException("champ « data » manquant"))
                    .split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt() }.toIntArray(),
            )
            else -> throw IllegalArgumentException("type « $type » inconnu")
        }
    }

    /** « 83 55 00 00 » → 0x5583 (petit-boutiste). */
    fun parseLeHex(value: String): Long {
        val bytes = value.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt(16).toLong() }
        var result = 0L
        bytes.forEachIndexed { i, b -> result = result or (b shl (8 * i)) }
        return result
    }

    fun toLeHex(value: Long): String =
        (0 until 4).joinToString(" ") { i -> "%02X".format((value shr (8 * i)) and 0xFF) }

    fun write(entries: List<Entry>, headerComment: String? = null): String = buildString {
        append("Filetype: IR signals file\nVersion: 1\n")
        headerComment?.lines()?.forEach { append("# ").append(it).append('\n') }
        for (e in entries) {
            append("#\n")
            append("name: ").append(e.name).append('\n')
            if (e.isRaw) {
                append("type: raw\n")
                append("frequency: ").append(e.frequency).append('\n')
                append("duty_cycle: ").append("%.6f".format(java.util.Locale.ROOT, e.dutyCycle)).append('\n')
                append("data: ").append(e.data.joinToString(" ")).append('\n')
            } else {
                append("type: parsed\n")
                append("protocol: ").append(e.protocol).append('\n')
                append("address: ").append(toLeHex(e.address)).append('\n')
                append("command: ").append(toLeHex(e.command)).append('\n')
            }
        }
    }
}
