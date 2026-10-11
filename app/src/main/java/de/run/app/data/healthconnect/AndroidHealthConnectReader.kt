package de.run.app.data.healthconnect

import android.content.Context
import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.run.app.data.local.HealthConnectReader
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import java.io.IOException
import java.time.Instant
import kotlin.math.roundToInt

class AndroidHealthConnectReader(context: Context) : HealthConnectReader {
    private val appContext = context.applicationContext

    override suspend fun readWorkouts(
        from: Instant,
        to: Instant,
        readInBackground: Boolean
    ): WorkoutReadResult {
        if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) {
            return WorkoutReadResult.Unavailable
        }
        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(HealthConnectWorkoutPermissions.requiredForImport)) {
                return WorkoutReadResult.PermissionNeeded
            }
            if (readInBackground && BACKGROUND_READ_PERMISSION !in granted) {
                return WorkoutReadResult.PermissionNeeded
            }
            val hasFullHistoryAccess = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
            val historyFrom = if (hasFullHistoryAccess) from else maxOf(from, Instant.now().minusSeconds(DEFAULT_HISTORY_DAYS * SECONDS_PER_DAY))
            WorkoutReadResult.Available(
                readRunningSessions(client, historyFrom, to, granted),
                hasFullHistoryAccess
            )
        } catch (_: SecurityException) {
            WorkoutReadResult.PermissionNeeded
        } catch (_: IOException) {
            WorkoutReadResult.Failed
        } catch (_: RemoteException) {
            WorkoutReadResult.Failed
        } catch (_: IllegalStateException) {
            WorkoutReadResult.Unavailable
        }
    }

    private suspend fun readRunningSessions(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
        grantedPermissions: Set<String>
    ): List<Workout> = readRecords<ExerciseSessionRecord>(client, from, to)
        .filter(::isRunning)
        .map { session -> toWorkout(client, session, grantedPermissions) }

    private suspend fun toWorkout(
        client: HealthConnectClient,
        session: ExerciseSessionRecord,
        grantedPermissions: Set<String>
    ): Workout {
        val sourcePackage = session.metadata.dataOrigin.packageName
        val distance = if (HealthConnectWorkoutPermissions.distances in grantedPermissions) {
            readRecords<DistanceRecord>(client, session.startTime, session.endTime, sourcePackage)
                .sumOf { it.distance.inMeters }
                .takeIf { it > 0.0 }
        } else null
        val heartRates = if (HealthConnectWorkoutPermissions.heartRates in grantedPermissions) {
            readRecords<HeartRateRecord>(client, session.startTime, session.endTime, sourcePackage)
                .flatMap { record -> record.samples.map { sample -> sample.beatsPerMinute } }
        } else emptyList()
        return Workout(
            sourceId = "$sourcePackage:${session.metadata.id}",
            sourceName = HealthConnectSourceLabel.forPackage(sourcePackage),
            title = "Lauf",
            startedAtMillis = session.startTime.toEpochMilli(),
            endedAtMillis = session.endTime.toEpochMilli(),
            distanceMeters = distance,
            averageHeartRateBpm = heartRates.average().takeIf { it.isFinite() }?.roundToInt(),
            maximumHeartRateBpm = heartRates.maxOrNull()?.toInt()
        )
    }

    private suspend inline fun <reified T : Record> readRecords(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
        sourcePackage: String? = null
    ): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    dataOriginFilter = sourcePackage?.let { setOf(DataOrigin(it)) } ?: emptySet(),
                    pageToken = pageToken
                )
            )
            records += page.records
            pageToken = page.pageToken
        } while (!pageToken.isNullOrEmpty())
        return records
    }

    private fun isRunning(session: ExerciseSessionRecord): Boolean =
        session.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
            session.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL

    private companion object {
        const val DEFAULT_HISTORY_DAYS = 30L
        const val SECONDS_PER_DAY = 86_400L
        val BACKGROUND_READ_PERMISSION =
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }
}
