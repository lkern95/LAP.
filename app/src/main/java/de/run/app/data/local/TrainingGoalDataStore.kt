package de.run.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.repository.TrainingGoalHistoryUnavailableException
import de.run.app.domain.repository.TrainingGoalRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class TrainingGoalDataStore(private val dataStore: DataStore<Preferences>) : TrainingGoalRepository {
    constructor(context: Context) : this(context.trainingSettings)

    override fun observeHistory(): Flow<List<TrainingGoal>> = dataStore.data
        .onStart { ensureHistoryIsReadable() }
        .map { values -> decodeOrThrow(values[GOAL_HISTORY].orEmpty()) }

    override suspend fun saveAsCurrent(goal: TrainingGoal) {
        dataStore.edit { values ->
            val history = readOrMigrate(values)
            val replaced = history.map { previous ->
                if (previous.status == TrainingGoalStatus.ACTIVE) previous.copy(status = TrainingGoalStatus.REPLACED)
                else previous
            }.filterNot { it.id == goal.id }
            values[GOAL_HISTORY] = TrainingGoalHistoryCodec.encode(replaced + goal.copy(status = TrainingGoalStatus.ACTIVE))
        }
    }

    override suspend fun update(goal: TrainingGoal) {
        dataStore.edit { values ->
            val history = readOrMigrate(values)
            val index = history.indexOfFirst { it.id == goal.id }
            if (index < 0) throw TrainingGoalHistoryUnavailableException()
            values[GOAL_HISTORY] = TrainingGoalHistoryCodec.encode(history.toMutableList().apply { set(index, goal) })
        }
    }

    private suspend fun ensureHistoryIsReadable() {
        dataStore.edit { values -> readOrMigrate(values) }
    }

    private fun readOrMigrate(values: androidx.datastore.preferences.core.MutablePreferences): List<TrainingGoal> {
        val stored = values[GOAL_HISTORY]
        if (stored != null) {
            val decoded = (TrainingGoalHistoryCodec.decode(stored) as? GoalHistoryDecodeResult.Valid)
                ?: throw TrainingGoalHistoryUnavailableException()
            if (!decoded.needsVersionUpgrade) return decoded.goals
            val recoveredLegacyGoal = if (decoded.goals.isEmpty()) readLegacyGoal(values) else null
            val upgraded = decoded.goals + listOfNotNull(recoveredLegacyGoal)
            values[GOAL_HISTORY] = TrainingGoalHistoryCodec.encode(upgraded)
            return upgraded
        }

        val legacyGoal = readLegacyGoal(values)
        val history = listOfNotNull(legacyGoal)
        values[GOAL_HISTORY] = TrainingGoalHistoryCodec.encode(history)
        return history
    }

    private fun readLegacyGoal(values: androidx.datastore.preferences.core.MutablePreferences): TrainingGoal? {
        val savedDate = values[LEGACY_EVENT_DATE] ?: run {
            val hasPartialGoal = values[LEGACY_RACE_DISTANCE] != null ||
                values[LEGACY_RACE_GOAL] != null || values[LEGACY_TARGET_TIME] != null
            if (hasPartialGoal) throw TrainingGoalHistoryUnavailableException()
            return null
        }
        val date = runCatching { LocalDate.parse(savedDate) }.getOrNull()
            ?: throw TrainingGoalHistoryUnavailableException()
        val savedGoal = values[LEGACY_RACE_GOAL]
        val raceGoal = savedGoal?.let { value -> RaceGoal.entries.firstOrNull { it.name == value } }
            ?: if (savedGoal == null) RaceGoal.ARRIVE else throw TrainingGoalHistoryUnavailableException()
        val distance = values[LEGACY_RACE_DISTANCE] ?: DEFAULT_RACE_DISTANCE_METERS
        val targetTime = values[LEGACY_TARGET_TIME]
        if (distance <= 0 || targetTime != null && targetTime <= 0) {
            throw TrainingGoalHistoryUnavailableException()
        }
        return TrainingGoal(
            id = UUID.randomUUID().toString(),
            raceDate = date,
            raceDistanceMeters = distance,
            raceGoal = raceGoal,
            targetTimeMinutes = targetTime
        )
    }

    private fun decodeOrThrow(raw: String): List<TrainingGoal> =
        (TrainingGoalHistoryCodec.decode(raw) as? GoalHistoryDecodeResult.Valid)?.goals
            ?: throw TrainingGoalHistoryUnavailableException()

    private companion object {
        val GOAL_HISTORY = stringPreferencesKey("training_goal_history_v1")
        val LEGACY_EVENT_DATE = stringPreferencesKey("event_date")
        val LEGACY_RACE_DISTANCE = intPreferencesKey("race_distance_meters")
        val LEGACY_RACE_GOAL = stringPreferencesKey("race_goal")
        val LEGACY_TARGET_TIME = intPreferencesKey("target_time_minutes")
        const val DEFAULT_RACE_DISTANCE_METERS = 21_100
    }
}
