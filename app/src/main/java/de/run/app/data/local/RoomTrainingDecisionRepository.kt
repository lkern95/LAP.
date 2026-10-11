package de.run.app.data.local

import de.run.app.domain.model.TrainingDecisionSnapshot
import de.run.app.domain.repository.TrainingDecisionRepository

class RoomTrainingDecisionRepository(private val dao: TrainingDecisionDao) : TrainingDecisionRepository {
    override suspend fun recordCurrent(snapshot: TrainingDecisionSnapshot) {
        dao.saveIfChanged(TrainingDecisionEntity.fromDomain(snapshot))
    }
}
