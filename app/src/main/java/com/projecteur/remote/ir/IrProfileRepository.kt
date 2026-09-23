package com.projecteur.remote.ir

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Profils IR disponibles hors ligne :
 *  - la base intégrée (copie non modifiée du dossier « Projectors » de Flipper-IRDB, CC0),
 *  - les fichiers .ir importés par l'utilisateur,
 *  - les commandes apprises avec un récepteur IR.
 */
class IrProfileRepository(private val context: Context) {

    private val _profiles = MutableStateFlow<List<IrProfile>>(emptyList())
    val profiles: StateFlow<List<IrProfile>> = _profiles.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val importedDir get() = File(context.filesDir, "imported").apply { mkdirs() }
    private val learnedFile get() = File(context.filesDir, "learned.ir")

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            val bundled = ArrayList<IrProfile>()
            walkAssets(ASSET_ROOT) { path ->
                val text = context.assets.open(path).bufferedReader().use { it.readText() }
                bundled += profileFromPath(path.removePrefix("$ASSET_ROOT/"), text, IrProfile.Source.BUNDLED)
            }
            val imported = importedDir.listFiles { f -> f.name.endsWith(".ir", true) }.orEmpty().map { f ->
                profileFromPath("Importés/${f.name}", f.readText(), IrProfile.Source.IMPORTED, idPrefix = "imported:")
            }
            _profiles.value = listOfNotNull(learnedProfile()) +
                imported.sortedBy { it.displayName.lowercase() } +
                bundled.sortedBy { it.displayName.lowercase() }
            _loadError.value = null
        } catch (e: Exception) {
            _loadError.value = "Impossible de charger les profils IR : ${e.message}"
        }
    }

    private fun walkAssets(path: String, onFile: (String) -> Unit) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            if (path.endsWith(".ir", ignoreCase = true)) onFile(path)
            return
        }
        for (child in children) walkAssets("$path/$child", onFile)
    }

    fun byId(id: String?): IrProfile? = id?.let { i -> _profiles.value.firstOrNull { it.id == i } }

    val brands: List<String> get() = _profiles.value.map { it.brand }.distinct().sortedBy { it.lowercase() }

    fun search(query: String, brand: String? = null): List<IrProfile> =
        ProfileSearch.search(_profiles.value, query, brand)

    fun bestMatch(brand: String?, model: String?): IrProfile? =
        ProfileSearch.bestMatch(_profiles.value, brand, model)

    /** Importe un fichier .ir (format Flipper) choisi par l'utilisateur. */
    suspend fun import(uri: Uri, displayName: String): Result<IrProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Fichier illisible")
            val parsed = FlipperIrFormat.parse(text)
            require(parsed.entries.isNotEmpty()) {
                "Aucun signal IR reconnu dans ce fichier (format Flipper « IR signals file » attendu)."
            }
            val safeName = displayName.replace(Regex("[^A-Za-z0-9 _.-]"), "_").let {
                if (it.endsWith(".ir", true)) it else "$it.ir"
            }
            File(importedDir, safeName).writeText(text)
            load()
            byId("imported:Importés/$safeName") ?: error("Profil importé introuvable après chargement")
        }
    }

    // --- Commandes apprises ------------------------------------------------------------------

    fun learnedEntries(): List<FlipperIrFormat.Entry> =
        if (learnedFile.exists()) FlipperIrFormat.parse(learnedFile.readText()).entries else emptyList()

    private fun learnedProfile(): IrProfile? {
        val entries = learnedEntries()
        if (entries.isEmpty()) return null
        return IrProfile(LEARNED_ID, "Mes commandes", "apprises", IrProfile.Source.LEARNED, entries)
    }

    suspend fun saveLearned(entry: FlipperIrFormat.Entry) = withContext(Dispatchers.IO) {
        val others = learnedEntries().filterNot { it.name.equals(entry.name, ignoreCase = true) }
        learnedFile.writeText(FlipperIrFormat.write(others + entry, "Commandes apprises avec Projecteur Remote"))
        load()
    }

    suspend fun deleteLearned(name: String) = withContext(Dispatchers.IO) {
        val remaining = learnedEntries().filterNot { it.name == name }
        if (remaining.isEmpty()) learnedFile.delete()
        else learnedFile.writeText(FlipperIrFormat.write(remaining, "Commandes apprises avec Projecteur Remote"))
        load()
    }

    companion object {
        const val ASSET_ROOT = "irdb/Projectors"
        const val LEARNED_ID = "learned"

        fun profileFromPath(
            relativePath: String,
            text: String,
            source: IrProfile.Source,
            idPrefix: String = "",
        ): IrProfile {
            val parts = relativePath.split('/')
            val brandFolder = parts.first()
            val brand = if (brandFolder == "BrandUnknown") "Marque inconnue" else brandFolder
            val fileName = parts.last().removeSuffix(".ir").removeSuffix(".IR")
            val model = fileName
                .replace(Regex("^${Regex.escape(brandFolder)}[ _-]*", RegexOption.IGNORE_CASE), "")
                .replace('_', ' ')
                .trim()
                .ifEmpty { "générique" }
            val parsed = FlipperIrFormat.parse(text)
            return IrProfile(
                id = idPrefix + relativePath,
                brand = brand,
                model = model,
                source = source,
                entries = parsed.entries,
                comments = parsed.comments,
                warnings = parsed.warnings,
            )
        }
    }
}

/** Recherche de profils, séparée pour être testable sans Android. */
object ProfileSearch {
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun search(profiles: List<IrProfile>, query: String, brand: String? = null): List<IrProfile> {
        val tokens = query.lowercase().split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }
        return profiles.filter { p ->
            (brand == null || p.brand.equals(brand, ignoreCase = true)) &&
                tokens.all { t -> norm(p.brand + p.model + p.id).contains(t) }
        }
    }

    /**
     * Profil correspondant exactement à une marque et un modèle connus, sinon null.
     * On ne sélectionne automatiquement que si la correspondance est non ambiguë.
     */
    fun bestMatch(profiles: List<IrProfile>, brand: String?, model: String?): IrProfile? {
        if (brand.isNullOrBlank()) return null
        val b = norm(brand)
        val sameBrand = profiles.filter {
            it.source == IrProfile.Source.BUNDLED || it.source == IrProfile.Source.IMPORTED
        }.filter { norm(it.brand) == b || b.startsWith(norm(it.brand)) && norm(it.brand).length >= 3 }
        if (sameBrand.isEmpty() || model.isNullOrBlank()) return null
        val m = norm(model)
        if (m.length < 2) return null
        val exact = sameBrand.filter { norm(it.model) == m }
        if (exact.size == 1) return exact.first()
        val contains = sameBrand.filter { norm(it.model).contains(m) || (norm(it.model).length >= 3 && m.contains(norm(it.model))) }
        return contains.singleOrNull()
    }
}
