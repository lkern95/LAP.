package de.run.app.application

import de.run.app.domain.repository.FitActivityImportRepository

class ImportFitActivity(private val repository: FitActivityImportRepository) {
    suspend operator fun invoke(uri: String) = repository.importFile(uri)
}
