package de.run.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.AvailabilityStatus
import de.run.app.domain.model.TrainingDayAvailability
import de.run.app.domain.model.TrainingPlanMode
import de.run.app.domain.repository.TrainingProfileRepository
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal val Context.trainingSettings by preferencesDataStore(name = "training_settings")

class TrainingProfileDataStore(private val context: Context) : TrainingProfileRepository {
    override fun observe(): Flow<TrainingProfile?> = context.trainingSettings.data
        .onStart { removeObsoleteReviewSetting() }
        .map { values ->
            val isConfigured = values[PROFILE_CONFIGURED] ?: (values[LEGACY_EVENT_DATE] != null)
            if (!isConfigured) return@map null
            TrainingProfile(
                longestComfortableRunMinutes = values[LONGEST_RUN_MINUTES] ?: DEFAULT_LONGEST_RUN_MINUTES,
                optionalRun = values[OPTIONAL_RUN] ?: false,
                strengthSessionsPerWeek = values[STRENGTH_SESSIONS] ?: 1,
                availableRunDays = parseDays(values[AVAILABLE_RUN_DAYS]),
                longRunDay = values[LONG_RUN_DAY]?.let { saved ->
                    DayOfWeek.entries.firstOrNull { it.name == saved }
                },
                runTimeLimitsByDay = parseTimeLimits(values[RUN_TIME_LIMITS]),
                availabilityMode = values[AVAILABILITY_MODE]?.let { saved ->
                    AvailabilityMode.entries.firstOrNull { it.name == saved }
                } ?: AvailabilityMode.RECURRING,
                datedAvailability = parseDatedAvailability(values[DATED_AVAILABILITY]),
                considerNightShiftRecovery = values[NIGHT_SHIFT_RECOVERY] ?: false,
                usualRunTimeLimitMinutes = (values[USUAL_RUN_TIME_LIMIT] ?: DEFAULT_USUAL_RUN_TIME_LIMIT)
                    .coerceIn(MIN_RUN_LIMIT, MAX_RUN_LIMIT),
                occasionalRunTimeLimitMinutes = values[OCCASIONAL_RUN_TIME_LIMIT]
                    ?.coerceIn(MIN_RUN_LIMIT, MAX_RUN_LIMIT),
                planMode = values[PLAN_MODE]?.let { saved ->
                    TrainingPlanMode.entries.firstOrNull { it.name == saved }
                } ?: TrainingPlanMode.RACE
            )
        }

    override suspend fun save(profile: TrainingProfile) {
        context.trainingSettings.edit { values ->
            values[PROFILE_CONFIGURED] = true
            values[LONGEST_RUN_MINUTES] = profile.longestComfortableRunMinutes
            values[OPTIONAL_RUN] = profile.optionalRun
            values[STRENGTH_SESSIONS] = profile.strengthSessionsPerWeek
            values[AVAILABLE_RUN_DAYS] = profile.availableRunDays.joinToString(",") { it.name }
            profile.longRunDay?.let { values[LONG_RUN_DAY] = it.name } ?: values.remove(LONG_RUN_DAY)
            values[RUN_TIME_LIMITS] = profile.runTimeLimitsByDay.entries.joinToString(";") { (day, minutes) ->
                "${day.name}=$minutes"
            }
            values[AVAILABILITY_MODE] = profile.availabilityMode.name
            values[USUAL_RUN_TIME_LIMIT] = profile.usualRunTimeLimitMinutes
            profile.occasionalRunTimeLimitMinutes?.let { values[OCCASIONAL_RUN_TIME_LIMIT] = it }
                ?: values.remove(OCCASIONAL_RUN_TIME_LIMIT)
            values[PLAN_MODE] = profile.planMode.name
            values[DATED_AVAILABILITY] = profile.datedAvailability.entries.joinToString(";") { (date, availability) ->
                val minutes = availability.maximumMinutes ?: 0
                val afterNightShift = if (availability.afterNightShift) 1 else 0
                "${date}=${availability.status.name}:$minutes:$afterNightShift"
            }
            values[NIGHT_SHIFT_RECOVERY] = profile.considerNightShiftRecovery
            values.remove(LEGACY_REVIEW_SETTING)
        }
    }

    private suspend fun removeObsoleteReviewSetting() {
        if (context.trainingSettings.data.first()[LEGACY_REVIEW_SETTING] != null) {
            context.trainingSettings.edit { values -> values.remove(LEGACY_REVIEW_SETTING) }
        }
    }

    private fun parseDays(value: String?): Set<DayOfWeek> {
        if (value == null) return DEFAULT_AVAILABLE_RUN_DAYS
        return value.split(',')
            .mapNotNull { saved -> DayOfWeek.entries.firstOrNull { it.name == saved } }
            .toSet()
    }

    private fun parseDatedAvailability(value: String?): Map<LocalDate, TrainingDayAvailability> = value
        .orEmpty().split(';').mapNotNull { item ->
            val (dateValue, entry) = item.split('=', limit = 2).let { it.getOrNull(0) to it.getOrNull(1) }
            val date = dateValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val fields = entry.orEmpty().split(':')
            val status = fields.getOrNull(0)?.let { saved -> AvailabilityStatus.entries.firstOrNull { it.name == saved } }
                ?.takeIf { it != AvailabilityStatus.UNKNOWN } ?: return@mapNotNull null
            val minutes = fields.getOrNull(1)?.toIntOrNull()?.takeIf { it in MIN_RUN_LIMIT..MAX_RUN_LIMIT }
            val afterNightShift = fields.getOrNull(2) == "1"
            date to TrainingDayAvailability(status, minutes, afterNightShift)
        }.toMap()

    private fun parseTimeLimits(value: String?): Map<DayOfWeek, Int> {
        val saved = value.orEmpty().split(';').mapNotNull { item ->
            val pieces = item.split('=')
            if (pieces.size != 2) return@mapNotNull null
            val day = DayOfWeek.entries.firstOrNull { it.name == pieces[0] } ?: return@mapNotNull null
            val minutes = pieces[1].toIntOrNull()?.takeIf { it in MIN_RUN_LIMIT..MAX_RUN_LIMIT }
                ?: return@mapNotNull null
            day to minutes
        }.toMap()
        return DEFAULT_RUN_TIME_LIMITS + saved
    }

    private companion object {
        const val DEFAULT_LONGEST_RUN_MINUTES = 45
        val PROFILE_CONFIGURED = booleanPreferencesKey("profile_configured")
        val PLAN_MODE = stringPreferencesKey("plan_mode")
        val LEGACY_EVENT_DATE = stringPreferencesKey("event_date")
        val LONGEST_RUN_MINUTES = intPreferencesKey("longest_run_minutes")
        val OPTIONAL_RUN = booleanPreferencesKey("optional_run")
        val STRENGTH_SESSIONS = intPreferencesKey("strength_sessions")
        val AVAILABLE_RUN_DAYS = stringPreferencesKey("available_run_days")
        val LONG_RUN_DAY = stringPreferencesKey("long_run_day")
        val RUN_TIME_LIMITS = stringPreferencesKey("run_time_limits")
        val AVAILABILITY_MODE = stringPreferencesKey("availability_mode")
        val USUAL_RUN_TIME_LIMIT = intPreferencesKey("usual_run_time_limit")
        val OCCASIONAL_RUN_TIME_LIMIT = intPreferencesKey("occasional_run_time_limit")
        val DATED_AVAILABILITY = stringPreferencesKey("dated_availability")
        val NIGHT_SHIFT_RECOVERY = booleanPreferencesKey("night_shift_recovery")
        val LEGACY_REVIEW_SETTING = booleanPreferencesKey("symptoms_need_review")
        val DEFAULT_AVAILABLE_RUN_DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
        val DEFAULT_RUN_TIME_LIMITS = mapOf(DayOfWeek.TUESDAY to 45, DayOfWeek.THURSDAY to 45, DayOfWeek.SUNDAY to 90)
        const val MIN_RUN_LIMIT = 30
        const val MAX_RUN_LIMIT = 180
        const val DEFAULT_USUAL_RUN_TIME_LIMIT = 45
    }
}
