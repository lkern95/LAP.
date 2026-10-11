package de.run.app.data.demo

import android.content.Context
import de.run.app.BuildConfig
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.repository.ReadinessHealthRepository
import de.run.app.domain.repository.WorkoutRepository
import java.time.Instant
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
            isDemo = true
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
        val workouts: List<Workout>
    ) {
        companion object {
            fun from(json: org.json.JSONObject): DemoFixture {
                val now = Instant.now()
                val records = json.getJSONArray("workouts")
                val workouts = (0 until records.length()).map { index ->
                    val item = records.getJSONObject(index)
                    val daysAgo = item.getInt("daysAgo").toLong()
                    val endedAt = now.minusSeconds(daysAgo * SECONDS_PER_DAY)
                    val durationSeconds = item.getInt("durationMinutes") * 60L
                    Workout(
                        sourceId = "demo:run:$index",
                        sourceName = GARMIN_SOURCE,
                        title = "Lauf",
                        startedAtMillis = endedAt.minusSeconds(durationSeconds).toEpochMilli(),
                        endedAtMillis = endedAt.toEpochMilli(),
                        distanceMeters = item.getDouble("distanceMeters"),
                        averageHeartRateBpm = item.optInt("averageHeartRateBpm", 0).takeIf { it > 0 }
                    )
                }
                return DemoFixture(
                    sleepMinutes = json.getInt("sleepMinutes"),
                    restingHeartRateBpm = json.getInt("restingHeartRateBpm"),
                    workouts = workouts
                )
            }
        }
    }

    private companion object {
        const val FIXTURE_NAME = "run_demo_data.json"
        const val GARMIN_SOURCE = "Garmin Connect"
        const val SECONDS_PER_DAY = 86_400L
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
