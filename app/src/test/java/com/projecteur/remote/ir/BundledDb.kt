package com.projecteur.remote.ir

import java.io.File

/** Accès aux fichiers de la base intégrée depuis les tests JVM. */
object BundledDb {
    val root = File("src/main/assets/irdb/Projectors")

    fun load(relative: String): FlipperIrFormat.ParseResult = FlipperIrFormat.parse(File(root, relative).readText())

    fun allFiles(): List<File> = root.walkTopDown().filter { it.isFile && it.name.endsWith(".ir") }.toList()

    fun profiles(): List<IrProfile> = allFiles().map { f ->
        IrProfileRepository.profileFromPath(f.relativeTo(root).invariantSeparatorsPath, f.readText(), IrProfile.Source.BUNDLED)
    }
}
