package de.run.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.run.app.domain.model.StrengthSessionProgress
import de.run.app.domain.model.StrengthSetProgress
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "strength_sessions")
data class StrengthSessionEntity(
    @PrimaryKey val sessionId: String,
    val workoutId: String,
    val catalogSnapshot: String,
    val status: String,
    val currentExerciseId: String?,
    val currentRound: Int,
    val completedSetsJson: String,
    val isCurrentSetStarted: Boolean,
    val pauseUntilMillis: Long?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?,
    val schemaVersion: String
) {
    fun toDomain(): StrengthSessionProgress {
        val values = JSONArray(completedSetsJson)
        val sets = List(values.length()) { index ->
            val item = values.getJSONObject(index)
            StrengthSetProgress(item.getString("exerciseId"), item.getInt("round"), item.getLong("completedAtMillis"))
        }
        return StrengthSessionProgress(
            sessionId, workoutId, catalogSnapshot, StrengthSessionProgress.Status.valueOf(status.uppercase()),
            currentExerciseId, currentRound, sets, isCurrentSetStarted, pauseUntilMillis,
            startedAtMillis, finishedAtMillis, schemaVersion
        )
    }

    companion object {
        fun fromDomain(value: StrengthSessionProgress) = StrengthSessionEntity(
            sessionId = value.sessionId,
            workoutId = value.workoutId,
            catalogSnapshot = value.catalogSnapshot,
            status = value.status.name.lowercase(),
            currentExerciseId = value.currentExerciseId,
            currentRound = value.currentRound,
            completedSetsJson = JSONArray().apply {
                value.completedSets.forEach { put(JSONObject().put("exerciseId", it.exerciseId).put("round", it.round).put("completedAtMillis", it.completedAtMillis)) }
            }.toString(),
            isCurrentSetStarted = value.isCurrentSetStarted,
            pauseUntilMillis = value.pauseUntilMillis,
            startedAtMillis = value.startedAtMillis,
            finishedAtMillis = value.finishedAtMillis,
            schemaVersion = value.schemaVersion
        )
    }
}
