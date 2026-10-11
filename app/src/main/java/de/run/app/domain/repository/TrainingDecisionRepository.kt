package de.run.app.domain.repository

import de.run.app.domain.model.TrainingDecisionSnapshot

interface TrainingDecisionRepository {
    suspend fun recordCurrent(snapshot: TrainingDecisionSnapshot)
}
