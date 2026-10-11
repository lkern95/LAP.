package de.run.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.RunningSymptomFeedback

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val sourceId: String,
    val sourceName: String,
    val title: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val distanceMeters: Double?,
    val averageHeartRateBpm: Int?,
    val maximumHeartRateBpm: Int?,
    val perceivedEffort: Int? = null,
    val runningAffectedBySymptoms: String? = null,
    val feedbackRecordedAtMillis: Long? = null
) {
    fun toDomain() = Workout(
        sourceId, sourceName, title, startedAtMillis, endedAtMillis,
        distanceMeters, averageHeartRateBpm, maximumHeartRateBpm,
        if (feedbackRecordedAtMillis != null) WorkoutFeedback(
            perceivedEffort,
            runningAffectedBySymptoms?.let { runCatching { RunningSymptomFeedback.valueOf(it) }.getOrNull() },
            feedbackRecordedAtMillis
        ) else null
    )

    companion object {
        fun fromDomain(workout: Workout) = WorkoutEntity(
            workout.sourceId,
            workout.sourceName,
            workout.title,
            workout.startedAtMillis,
            workout.endedAtMillis,
            workout.distanceMeters,
            workout.averageHeartRateBpm,
            workout.maximumHeartRateBpm,
            workout.feedback?.perceivedEffort,
            workout.feedback?.runningAffectedBySymptoms?.name,
            workout.feedback?.recordedAtMillis
        )
    }
}
