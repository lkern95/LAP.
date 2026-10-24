package de.run.app.domain.repository

import de.run.app.domain.model.StrengthCatalog

interface StrengthCatalogRepository {
    suspend fun load(): StrengthCatalog
    suspend fun import(json: String): CatalogImportResult
}

data class CatalogImportResult(val addedExerciseCount: Int, val addedWorkoutCount: Int)
