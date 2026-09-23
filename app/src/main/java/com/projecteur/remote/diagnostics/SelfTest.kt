package com.projecteur.remote.diagnostics

/** Ligne de la checklist de diagnostic automatique. */
data class CheckItem(val title: String, val status: Status, val detail: String) {
    enum class Status(val symbol: String, val label: String) {
        AVAILABLE("✓", "Disponible"),
        UNAVAILABLE("✗", "Indisponible"),
        NEEDS_ACCESSORY("⚠", "Nécessite un accessoire"),
    }
}
