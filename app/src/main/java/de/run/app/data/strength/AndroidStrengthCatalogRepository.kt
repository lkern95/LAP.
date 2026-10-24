package de.run.app.data.strength

import android.content.Context
import de.run.app.domain.model.StrengthCatalog
import de.run.app.domain.repository.CatalogImportResult
import de.run.app.domain.repository.StrengthCatalogRepository
import java.io.File

class AndroidStrengthCatalogRepository(
    context: Context,
    private val parser: StrengthCatalogJsonParser = StrengthCatalogJsonParser()
) : StrengthCatalogRepository {
    private val appContext = context.applicationContext
    private val overrideFile = File(appContext.filesDir, "strength/catalog.json")

    override suspend fun load(): StrengthCatalog {
        val text = if (overrideFile.isFile) overrideFile.readText() else {
            appContext.assets.open("strength/runner_training_catalog_v2.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        return parser.parse(text)
    }

    override suspend fun import(json: String): CatalogImportResult {
        val candidate = parser.parse(json)
        val current = load()
        require(candidate.rawExerciseJson.keys.containsAll(current.rawExerciseJson.keys)) {
            "Der Import muss vorhandene Übungs-IDs enthalten."
        }
        require(candidate.rawWorkoutJson.keys.containsAll(current.rawWorkoutJson.keys)) {
            "Der Import muss vorhandene Programm-IDs enthalten."
        }
        require(current.rawExerciseJson.all { (id, json) -> candidate.rawExerciseJson[id] == json }) {
            "Eine vorhandene Übung hat geänderte Inhalte. Verwende dafür eine neue Übungs-ID."
        }
        require(current.rawWorkoutJson.all { (id, json) -> candidate.rawWorkoutJson[id] == json }) {
            "Ein vorhandenes Programm hat geänderte Inhalte. Verwende dafür eine neue Programm-ID."
        }
        val file = overrideFile
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "catalog.json.tmp")
        temporary.writeText(candidate.sourceJson, Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            error("Der Katalog konnte nicht gespeichert werden.")
        }
        return CatalogImportResult(
            candidate.exercises.size - current.exercises.size,
            candidate.workouts.size - current.workouts.size
        )
    }
}
