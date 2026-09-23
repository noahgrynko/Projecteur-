package com.projecteur.remote.ir

import com.projecteur.remote.core.RemoteCommand

/** Profil de télécommande : un fichier de codes pour une marque/un modèle. */
data class IrProfile(
    val id: String,
    val brand: String,
    val model: String,
    val source: Source,
    val entries: List<FlipperIrFormat.Entry>,
    val comments: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    enum class Source(val label: String) {
        BUNDLED("Base intégrée Flipper-IRDB (CC0)"),
        IMPORTED("Fichier importé"),
        LEARNED("Commandes apprises"),
    }

    val commands: Map<RemoteCommand, FlipperIrFormat.Entry> by lazy { CommandMapper.map(entries) }

    /** Fréquence porteuse dominante (information pour l'utilisateur). */
    val carrierSummary: String by lazy {
        val freqs = entries.mapNotNull { e ->
            if (e.isRaw) e.frequency else runCatching { e.toSignal().carrierHz }.getOrNull()
        }.distinct().sorted()
        if (freqs.isEmpty()) "—" else freqs.joinToString(", ") { "%.1f kHz".format(java.util.Locale.ROOT, it / 1000.0) }
    }

    val protocolSummary: String by lazy {
        entries.map { if (it.isRaw) "brut" else it.protocol ?: "?" }.distinct().sorted().joinToString(", ")
    }

    val unsupportedCount: Int get() = entries.count { !it.isSupported }

    val displayName: String get() = if (model.isBlank()) brand else "$brand — $model"
}
