package de.run.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.WorkoutPlanMatch
import java.time.LocalDate

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
    val feedbackRecordedAtMillis: Long? = null,
    val activityType: String = "RUNNING",
    val averageCadenceRpm: Int? = null,
    val planMatchType: String? = null,
    val plannedTrainingId: String? = null,
    val plannedDateEpochDay: Long? = null,
    val plannedTitle: String? = null,
    val plannedDurationMinutes: Int? = null,
    val plannedPaceSlowerSecondsPerKm: Int? = null,
    val plannedPaceFasterSecondsPerKm: Int? = null,
    val plannedEffortMinimum: Int? = null,
    val plannedEffortMaximum: Int? = null,
    val plannedAssessment: String? = null,
    val plannedRationale: String? = null
) {
    fun toDomain() = Workout(
        sourceId, sourceName, title, startedAtMillis, endedAtMillis,
        distanceMeters, averageHeartRateBpm, maximumHeartRateBpm,
        if (feedbackRecordedAtMillis != null) WorkoutFeedback(
            perceivedEffort,
            runningAffectedBySymptoms?.let { runCatching { RunningSymptomFeedback.valueOf(it) }.getOrNull() },
            feedbackRecordedAtMillis
        ) else null,
        de.run.app.domain.model.WorkoutActivityType.fromStorage(activityType),
        averageCadenceRpm,
        if (planMatchType == null) null else WorkoutPlanMatch(
            type = runCatching { PlannedRunMatchType.valueOf(planMatchType.orEmpty()) }.getOrDefault(PlannedRunMatchType.AMBIGUOUS),
            trainingId = plannedTrainingId,
            scheduledDate = plannedDateEpochDay?.let(LocalDate::ofEpochDay),
            plannedTitle = plannedTitle,
            plannedDurationMinutes = plannedDurationMinutes,
            paceSlowerSecondsPerKm = plannedPaceSlowerSecondsPerKm,
            paceFasterSecondsPerKm = plannedPaceFasterSecondsPerKm,
            effortMinimum = plannedEffortMinimum,
            effortMaximum = plannedEffortMaximum,
            assessment = plannedAssessment?.let { value -> runCatching { PlannedRunAssessment.valueOf(value) }.getOrNull() },
            rationale = plannedRationale
        )
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
            workout.feedback?.recordedAtMillis,
            workout.activityType.name,
            workout.averageCadenceRpm,
            workout.planMatch?.type?.name,
            workout.planMatch?.trainingId,
            workout.planMatch?.scheduledDate?.toEpochDay(),
            workout.planMatch?.plannedTitle,
            workout.planMatch?.plannedDurationMinutes,
            workout.planMatch?.paceSlowerSecondsPerKm,
            workout.planMatch?.paceFasterSecondsPerKm,
            workout.planMatch?.effortMinimum,
            workout.planMatch?.effortMaximum,
            workout.planMatch?.assessment?.name,
            workout.planMatch?.rationale
        )
    }
}
