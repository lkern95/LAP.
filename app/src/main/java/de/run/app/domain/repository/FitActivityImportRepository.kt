package de.run.app.domain.repository

import de.run.app.domain.model.Workout

sealed interface FitActivityImportResult {
    data class Imported(val workouts: List<Workout>) : FitActivityImportResult
    data object NotActivityFile : FitActivityImportResult
    data object NoRunningSession : FitActivityImportResult
    data object InvalidFile : FitActivityImportResult
    data object FileTooLarge : FitActivityImportResult
    data object Failed : FitActivityImportResult
}

interface FitActivityImportRepository {
    suspend fun importFile(uri: String): FitActivityImportResult
}
