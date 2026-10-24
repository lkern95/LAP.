package de.run.app.domain.repository

import de.run.app.domain.model.Workout

sealed interface FitActivityImportResult {
    data class Imported(
        val workouts: List<Workout>,
        val foundActivities: Int = workouts.size,
        val newlyImportedActivities: Int = workouts.size,
        val alreadyPresentActivities: Int = 0,
        val savedOriginalFiles: Int = 0,
        val skippedFiles: Int = 0,
        val unsupportedFiles: Map<String, Int> = emptyMap()
    ) : FitActivityImportResult
    data object NotActivityFile : FitActivityImportResult
    data object NoActivitySessions : FitActivityImportResult
    data object InvalidFile : FitActivityImportResult
    data object FileTooLarge : FitActivityImportResult
    data object Failed : FitActivityImportResult
}

interface FitActivityImportRepository {
    suspend fun importFile(uri: String): FitActivityImportResult
}
