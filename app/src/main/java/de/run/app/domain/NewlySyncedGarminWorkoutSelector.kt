package de.run.app.domain

import de.run.app.domain.model.Workout

class NewlySyncedGarminWorkoutSelector {
    fun select(previouslySaved: List<Workout>, received: List<Workout>): Workout? {
        val savedIds = previouslySaved.asSequence().map(Workout::sourceId).toSet()
        return received.asSequence()
            .filter { it.sourceName in IMPORTED_GARMIN_SOURCES }
            .filterNot { it.sourceId.startsWith(DEMO_SOURCE_PREFIX) }
            .filter { it.sourceId !in savedIds && it.durationSeconds > 0L }
            .maxByOrNull(Workout::endedAtMillis)
    }

    private companion object {
        val IMPORTED_GARMIN_SOURCES = setOf("Garmin Connect", "Health Sync")
        const val DEMO_SOURCE_PREFIX = "demo:"
    }
}
