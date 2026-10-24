package de.run.app.data.fit

import com.garmin.fit.Decode
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.MesgListener
import com.garmin.fit.MesgNum
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutActivityType
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.math.roundToLong

internal class GarminFitActivityDecoder {
    fun decode(bytes: ByteArray): FitDecodeResult {
        if (!Decode().isFileFit(ByteArrayInputStream(bytes))) return FitDecodeResult.Invalid
        if (!Decode().checkFileIntegrity(ByteArrayInputStream(bytes))) return FitDecodeResult.Invalid

        var isActivity = false
        val sessions = mutableListOf<SessionMesg>()
        Decode().read(ByteArrayInputStream(bytes), MesgListener { message ->
            when (message.num) {
                MesgNum.FILE_ID -> isActivity = FileIdMesg(message).type == File.ACTIVITY
                MesgNum.SESSION -> sessions += SessionMesg(message)
            }
        })
        if (!isActivity) return FitDecodeResult.NotActivityFile

        if (sessions.isEmpty()) return FitDecodeResult.NoActivitySessions
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val workouts = sessions.mapIndexedNotNull { index, session -> session.toWorkout(digest, index) }
        return if (workouts.isEmpty()) FitDecodeResult.Invalid else FitDecodeResult.Activities(workouts)
    }

    private fun SessionMesg.toWorkout(fileHash: String, index: Int): Workout? {
        val start = startTime?.date?.time ?: return null
        val elapsedSeconds = totalElapsedTime?.toDouble()?.takeIf { it > 0.0 } ?: return null
        val durationMillis = elapsedSeconds.roundToLong() * MILLIS_PER_SECOND
        val distance = totalDistance?.toDouble()?.takeIf { it.isFinite() && it > 0.0 }
        val type = activityType(sport, subSport)
        return Workout(
            sourceId = "fit:$fileHash:$index",
            sourceName = "Garmin-Backup",
            title = type.label,
            startedAtMillis = start,
            endedAtMillis = start + durationMillis,
            distanceMeters = distance,
            averageHeartRateBpm = avgHeartRate?.toInt(),
            maximumHeartRateBpm = maxHeartRate?.toInt(),
            activityType = type,
            averageCadenceRpm = (avgRunningCadence ?: avgCadence)?.toInt()
        )
    }

    private fun activityType(sport: Sport?, subSport: SubSport?): WorkoutActivityType = when {
        sport == Sport.RUNNING -> WorkoutActivityType.RUNNING
        sport == Sport.WALKING -> WorkoutActivityType.WALKING
        sport == Sport.CYCLING || sport == Sport.E_BIKING -> WorkoutActivityType.CYCLING
        subSport == SubSport.STRENGTH_TRAINING || sport == Sport.TRAINING || sport == Sport.FITNESS_EQUIPMENT ->
            WorkoutActivityType.STRENGTH
        else -> WorkoutActivityType.OTHER
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

internal sealed interface FitDecodeResult {
    data class Activities(val workouts: List<Workout>) : FitDecodeResult
    data object NotActivityFile : FitDecodeResult
    data object NoActivitySessions : FitDecodeResult
    data object Invalid : FitDecodeResult
}
