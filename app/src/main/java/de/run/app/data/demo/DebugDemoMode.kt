package de.run.app.data.demo

import android.content.Context
import de.run.app.BuildConfig
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.WorkoutPlanMatch
import de.run.app.domain.model.DailyHealthValue
import de.run.app.domain.repository.ReadinessHealthRepository
import de.run.app.domain.repository.WorkoutRepository
import java.time.Instant
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

class DebugDemoModeController {
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (BuildConfig.DEBUG) mutableEnabled.value = enabled
    }
}

class DebugDemoDataSource(context: Context) {
    private val appContext = context.applicationContext

    fun workouts(): List<Workout> = fixture()?.workouts.orEmpty()

    fun healthSnapshot(): ReadinessHealthSnapshot {
        val fixture = fixture() ?: return ReadinessHealthSnapshot(
            unavailableMetric(), unavailableMetric(), isDemo = true
        )
        return ReadinessHealthSnapshot(
            sleepMinutes = availableMetric(fixture.sleepMinutes),
            restingHeartRateBpm = availableMetric(fixture.restingHeartRateBpm),
            isDemo = true,
            sleepHistory = dailyHistory(fixture.sleepMinutes, variation = 55),
            restingHeartRateHistory = dailyHistory(fixture.restingHeartRateBpm, variation = 6),
            hrvLastNightAverageMs = availableMetric(fixture.hrvLastNightAverageMs),
            hrvStatus = HealthMetric(fixture.hrvStatus, HealthMetricAvailability.AVAILABLE, DEMO_SOURCE),
            hrvHistory = dailyHistory(fixture.hrvHistoryMs),
            averageStressLevel = availableMetric(fixture.averageStressLevel),
            stressHistory = dailyHistory(fixture.averageStressLevel, variation = 34),
            steps = availableMetric(fixture.steps),
            stepsHistory = dailyHistory(fixture.steps, variation = 3_200)
        )
    }

    private fun fixture(): DemoFixture? {
        if (!BuildConfig.DEBUG) return null
        return try {
            val text = appContext.assets.open(FIXTURE_NAME).bufferedReader().use { it.readText() }
            DemoFixture.from(org.json.JSONObject(text))
        } catch (_: Exception) {
            null
        }
    }

    private data class DemoFixture(
        val sleepMinutes: Int,
        val restingHeartRateBpm: Int,
        val hrvLastNightAverageMs: Int,
        val hrvStatus: String,
        val hrvHistoryMs: List<Int>,
        val averageStressLevel: Int,
        val steps: Int,
        val workouts: List<Workout>
    ) {
        companion object {
            fun from(json: org.json.JSONObject): DemoFixture {
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val today = now.atZone(zone).toLocalDate()
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val records = json.getJSONArray("workouts")
                val workouts = (0 until records.length()).mapNotNull { index ->
                    val item = records.getJSONObject(index)
                    val runDate = weekStart.plusDays(item.getInt("weekDayOffset").toLong())
                    if (runDate.isAfter(today)) return@mapNotNull null
                    val scheduledEnd = runDate.atTime(12, 0).atZone(zone).toInstant()
                    val endedAt = minOf(now, scheduledEnd).minusSeconds(index * 60L)
                    val durationSeconds = item.getInt("durationMinutes") * 60L
                    Workout(
                        sourceId = "demo:run:$index",
                        sourceName = GARMIN_SOURCE,
                        title = "Lauf",
                        startedAtMillis = endedAt.minusSeconds(durationSeconds).toEpochMilli(),
                        endedAtMillis = endedAt.toEpochMilli(),
                        distanceMeters = item.getDouble("distanceMeters"),
                        averageHeartRateBpm = item.optInt("averageHeartRateBpm", 0).takeIf { it > 0 },
                        planMatch = if (item.optBoolean("isExtra", false)) {
                            WorkoutPlanMatch(
                                type = PlannedRunMatchType.EXTRA,
                                scheduledDate = runDate,
                                rationale = "Demolauf außerhalb der Planeinheiten."
                            )
                        } else null
                    )
                }
                return DemoFixture(
                    sleepMinutes = json.getInt("sleepMinutes"),
                    restingHeartRateBpm = json.getInt("restingHeartRateBpm"),
                    hrvLastNightAverageMs = json.getInt("hrvLastNightAverageMs"),
                    hrvStatus = json.getString("hrvStatus"),
                    hrvHistoryMs = json.getJSONArray("hrvHistoryMs").let { values ->
                        (0 until values.length()).map(values::getInt)
                    },
                    averageStressLevel = json.getInt("averageStressLevel"),
                    steps = json.getInt("steps"),
                    workouts = workouts
                )
            }
        }
    }

    private companion object {
        const val FIXTURE_NAME = "run_demo_data.json"
        const val GARMIN_SOURCE = "Garmin Connect"
    }
}

class DemoAwareWorkoutRepository(
    private val real: WorkoutRepository,
    private val mode: DebugDemoModeController,
    private val demoData: DebugDemoDataSource
) : WorkoutRepository {
    override fun observeRecent(): Flow<List<Workout>> = combine(mode.enabled, real.observeRecent()) { demo, records ->
        if (demo) demoData.workouts() else records
    }

    override suspend fun refresh(from: Instant, to: Instant, readInBackground: Boolean): WorkoutReadResult =
        if (mode.enabled.value) WorkoutReadResult.Available(demoData.workouts())
        else real.refresh(from, to, readInBackground)

    override suspend fun saveFeedback(sourceId: String, feedback: WorkoutFeedback): Boolean =
        if (mode.enabled.value) false else real.saveFeedback(sourceId, feedback)

    override suspend fun savePlanMatch(sourceId: String, match: WorkoutPlanMatch): Boolean =
        if (mode.enabled.value) false else real.savePlanMatch(sourceId, match)
}

class DemoAwareReadinessHealthRepository(
    private val real: ReadinessHealthRepository,
    private val mode: DebugDemoModeController,
    private val demoData: DebugDemoDataSource
) : ReadinessHealthRepository {
    override suspend fun read(from: Instant, to: Instant): ReadinessHealthSnapshot =
        if (mode.enabled.value) demoData.healthSnapshot() else real.read(from, to)
}

private fun availableMetric(value: Int) = HealthMetric(value, HealthMetricAvailability.AVAILABLE, "Demodaten")
private fun unavailableMetric() = HealthMetric<Int>(null, HealthMetricAvailability.UNAVAILABLE)
private fun dailyHistory(value: Int, variation: Int): List<DailyHealthValue> = (1L..DEMO_HISTORY_DAYS).map { daysAgo ->
    val index = daysAgo.toInt()
    val offset = ((index * 17) % (variation * 2 + 1)) - variation
    DailyHealthValue(java.time.LocalDate.now().minusDays(daysAgo), (value + offset).coerceAtLeast(1))
}

private fun dailyHistory(values: List<Int>): List<DailyHealthValue> = (0 until DEMO_HISTORY_DAYS).map { index ->
    DailyHealthValue(java.time.LocalDate.now().minusDays(index + 1L), values[index % values.size])
}

private const val DEMO_HISTORY_DAYS = 90

private const val DEMO_SOURCE = "Demodaten"
