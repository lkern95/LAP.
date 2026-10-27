package de.run.app.data.healthconnect

import android.content.Context
import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.run.app.domain.model.DailyHealthValue
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.repository.ReadinessHealthRepository
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectReadinessRepository(context: Context) : ReadinessHealthRepository {
    private val appContext = context.applicationContext

    override suspend fun read(from: Instant, to: Instant): ReadinessHealthSnapshot {
        if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) {
            return snapshot(HealthMetricAvailability.UNAVAILABLE)
        }
        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val permissions = client.permissionController.getGrantedPermissions()
            val (sleep, sleepHistory) = readSleep(client, from, to, permissions)
            val (pulse, pulseHistory) = readRestingHeartRate(client, from, to, permissions)
            ReadinessHealthSnapshot(
                sleepMinutes = sleep,
                restingHeartRateBpm = pulse,
                sleepHistory = sleepHistory,
                restingHeartRateHistory = pulseHistory
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
    ): Pair<HealthMetric<Int>, List<DailyHealthValue>> {
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) !in granted) {
            return metric(HealthMetricAvailability.PERMISSION_NEEDED) to emptyList()
        }
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        ).records
        val daily = records.groupBy { it.endTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapNotNull { (date, sessions) ->
                sessions.maxByOrNull(SleepSessionRecord::endTime)?.let { sleepMinutes(it)?.let { minutes -> DailyHealthValue(date, minutes) } }
            }
        val latest = records.filter { !it.endTime.isBefore(to.minus(CURRENT_VALUE_AGE)) }
            .maxByOrNull(SleepSessionRecord::endTime)
        val latestMinutes = latest?.let(::sleepMinutes)
        val metric = latestMinutes?.let {
            HealthMetric(it, HealthMetricAvailability.AVAILABLE, HealthConnectSourceLabel.forPackage(latest.metadata.dataOrigin.packageName))
        } ?: metric(HealthMetricAvailability.NO_DATA)
        return metric to daily
    }

    private fun sleepMinutes(session: SleepSessionRecord): Int? {
        val asleepMinutes = session.stages
            .filterNot { stage -> stage.stage in AWAKE_STAGE_TYPES }
            .sumOf { stage -> Duration.between(stage.startTime, stage.endTime).toMinutes() }
            .takeIf { it > 0 }
            ?: Duration.between(session.startTime, session.endTime).toMinutes()
        return asleepMinutes.toInt().takeIf { it > 0 }
    }

    private suspend fun readRestingHeartRate(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
        granted: Set<String>
    ): Pair<HealthMetric<Int>, List<DailyHealthValue>> {
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) !in granted) {
            return metric(HealthMetricAvailability.PERMISSION_NEEDED) to emptyList()
        }
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        ).records
        val daily = records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapNotNull { (date, values) -> values.maxByOrNull(RestingHeartRateRecord::time)?.let { DailyHealthValue(date, it.beatsPerMinute.toInt()) } }
        val latest = records.filter { !it.time.isBefore(to.minus(CURRENT_VALUE_AGE)) }
            .maxByOrNull(RestingHeartRateRecord::time)
        val metric = latest?.let {
            HealthMetric(
                value = it.beatsPerMinute.toInt(),
                availability = HealthMetricAvailability.AVAILABLE,
                sourceName = HealthConnectSourceLabel.forPackage(it.metadata.dataOrigin.packageName)
            )
        } ?: metric(HealthMetricAvailability.NO_DATA)
        return metric to daily
    }

    private companion object {
        val CURRENT_VALUE_AGE: Duration = Duration.ofHours(36)
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
