package de.run.app.data.healthconnect

import android.content.Context
import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.repository.ReadinessHealthRepository
import java.io.IOException
import java.time.Duration
import java.time.Instant

class HealthConnectReadinessRepository(context: Context) : ReadinessHealthRepository {
    private val appContext = context.applicationContext

    override suspend fun read(from: Instant, to: Instant): ReadinessHealthSnapshot {
        if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) {
            return snapshot(HealthMetricAvailability.UNAVAILABLE)
        }
        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val permissions = client.permissionController.getGrantedPermissions()
            ReadinessHealthSnapshot(
                sleepMinutes = readSleep(client, from, to, permissions),
                restingHeartRateBpm = readRestingHeartRate(client, from, to, permissions)
            )
        } catch (_: SecurityException) {
            snapshot(HealthMetricAvailability.PERMISSION_NEEDED)
        } catch (_: IOException) {
            snapshot(HealthMetricAvailability.FAILED)
        } catch (_: RemoteException) {
            snapshot(HealthMetricAvailability.FAILED)
        } catch (_: IllegalStateException) {
            snapshot(HealthMetricAvailability.UNAVAILABLE)
        }
    }

    private suspend fun readSleep(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
        granted: Set<String>
    ): HealthMetric<Int> {
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) !in granted) {
            return metric(HealthMetricAvailability.PERMISSION_NEEDED)
        }
        val session = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        ).records.maxByOrNull(SleepSessionRecord::endTime) ?: return metric(HealthMetricAvailability.NO_DATA)
        val asleepMinutes = session.stages
            .filterNot { stage -> stage.stage in AWAKE_STAGE_TYPES }
            .sumOf { stage -> Duration.between(stage.startTime, stage.endTime).toMinutes() }
            .takeIf { it > 0 }
            ?: Duration.between(session.startTime, session.endTime).toMinutes()
        return HealthMetric(
            value = asleepMinutes.toInt().takeIf { it > 0 },
            availability = if (asleepMinutes > 0) HealthMetricAvailability.AVAILABLE else HealthMetricAvailability.NO_DATA,
            sourceName = HealthConnectSourceLabel.forPackage(session.metadata.dataOrigin.packageName)
        )
    }

    private suspend fun readRestingHeartRate(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
        granted: Set<String>
    ): HealthMetric<Int> {
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) !in granted) {
            return metric(HealthMetricAvailability.PERMISSION_NEEDED)
        }
        val record = client.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        ).records.maxByOrNull(RestingHeartRateRecord::time) ?: return metric(HealthMetricAvailability.NO_DATA)
        return HealthMetric(
            value = record.beatsPerMinute.toInt(),
            availability = HealthMetricAvailability.AVAILABLE,
            sourceName = HealthConnectSourceLabel.forPackage(record.metadata.dataOrigin.packageName)
        )
    }

    private companion object {
        val AWAKE_STAGE_TYPES = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        )
    }
}

private fun snapshot(availability: HealthMetricAvailability) = ReadinessHealthSnapshot(
    sleepMinutes = metric(availability),
    restingHeartRateBpm = metric(availability)
)

private fun metric(availability: HealthMetricAvailability) = HealthMetric<Int>(null, availability)

