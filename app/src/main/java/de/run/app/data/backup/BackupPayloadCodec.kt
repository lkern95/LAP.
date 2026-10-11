package de.run.app.data.backup

import de.run.app.data.local.StrengthCompletionEntity
import de.run.app.data.local.TrainingDecisionEntity
import de.run.app.data.local.WorkoutEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal object BackupPayloadCodec {
    const val CURRENT_VERSION = 2
    private const val MAX_ITEMS = 100_000
    private const val MAX_STRING_BYTES = 1_000_000

    fun encode(snapshot: BackupSnapshot): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(CURRENT_VERSION)
            out.writeInt(snapshot.workouts.size)
            snapshot.workouts.forEach { it.writeCurrent(out) }
            out.writeInt(snapshot.strengthCompletions.size)
            snapshot.strengthCompletions.forEach { it.write(out) }
            out.writeInt(snapshot.trainingDecisions.size)
            snapshot.trainingDecisions.forEach { it.write(out) }
            out.writeInt(snapshot.stores.size)
            snapshot.stores.toSortedMap().forEach { (name, values) ->
                out.writeBounded(name)
                out.writeInt(values.size)
                values.sortedBy { it.name }.forEach { preference ->
                    out.writeBounded(preference.name)
                    out.writeBounded(preference.type)
                    out.writeBounded(preference.value)
                }
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray, expectedVersion: Int): BackupSnapshot {
        if (expectedVersion !in 1..CURRENT_VERSION) throw UnsupportedBackupVersion()
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val payloadVersion = input.readInt()
            if (payloadVersion != expectedVersion) throw InvalidBackupPayload()
            val workouts = List(input.readCount()) {
                if (payloadVersion == 1) input.readLegacyWorkout() else input.readCurrentWorkout()
            }
            val strengths = List(input.readCount()) { input.readStrength() }
            val decisions = if (payloadVersion >= 2) List(input.readCount()) { input.readDecision() } else emptyList()
            val stores = buildMap {
                repeat(input.readCount()) {
                    val storeName = input.readBounded()
                    val values = List(input.readCount()) {
                        BackupPreference(input.readBounded(), input.readBounded(), input.readBounded())
                    }
                    if (put(storeName, values) != null) throw InvalidBackupPayload()
                }
            }
            if (input.available() != 0) throw InvalidBackupPayload()
            BackupSnapshot(workouts, strengths, decisions, stores)
        }
    }

    private fun WorkoutEntity.writeCurrent(out: DataOutputStream) {
        out.writeBounded(sourceId); out.writeBounded(sourceName); out.writeBounded(title)
        out.writeLong(startedAtMillis); out.writeLong(endedAtMillis); out.writeNullableDouble(distanceMeters)
        out.writeNullableInt(averageHeartRateBpm); out.writeNullableInt(maximumHeartRateBpm)
        out.writeNullableInt(perceivedEffort); out.writeNullableString(runningAffectedBySymptoms)
        out.writeNullableLong(feedbackRecordedAtMillis)
    }

    private fun WorkoutEntity.writeLegacy(out: DataOutputStream) {
        out.writeBounded(sourceId); out.writeBounded(sourceName); out.writeBounded(title)
        out.writeLong(startedAtMillis); out.writeLong(endedAtMillis); out.writeNullableDouble(distanceMeters)
        out.writeNullableInt(averageHeartRateBpm); out.writeNullableInt(maximumHeartRateBpm)
    }

    private fun DataInputStream.readCurrentWorkout() = WorkoutEntity(
        sourceId = readBounded(), sourceName = readBounded(), title = readBounded(),
        startedAtMillis = readLong(), endedAtMillis = readLong(), distanceMeters = readNullableDouble(),
        averageHeartRateBpm = readNullableInt(), maximumHeartRateBpm = readNullableInt(),
        perceivedEffort = readNullableInt(), runningAffectedBySymptoms = readNullableString(),
        feedbackRecordedAtMillis = readNullableLong()
    )

    private fun DataInputStream.readLegacyWorkout() = WorkoutEntity(
        sourceId = readBounded(), sourceName = readBounded(), title = readBounded(),
        startedAtMillis = readLong(), endedAtMillis = readLong(), distanceMeters = readNullableDouble(),
        averageHeartRateBpm = readNullableInt(), maximumHeartRateBpm = readNullableInt()
    )

    private fun StrengthCompletionEntity.write(out: DataOutputStream) {
        out.writeBounded(id); out.writeBounded(exerciseId); out.writeBounded(completedOn)
    }

    private fun DataInputStream.readStrength() = StrengthCompletionEntity(readBounded(), readBounded(), readBounded())

    private fun TrainingDecisionEntity.write(out: DataOutputStream) {
        out.writeBounded(id); out.writeInt(ruleVersion); out.writeBounded(inputRevision)
        out.writeNullableString(targetRevision); out.writeBounded(evaluatedOn); out.writeInt(runCount)
        out.writeNullableLong(latestRunEndedAtMillis); out.writeNullableString(recommendationTitle)
        out.writeNullableInt(recommendationMinutes); out.writeBounded(reasonCode)
    }

    private fun DataInputStream.readDecision() = TrainingDecisionEntity(
        id = readBounded(), ruleVersion = readInt(), inputRevision = readBounded(),
        targetRevision = readNullableString(), evaluatedOn = readBounded(), runCount = readInt(),
        latestRunEndedAtMillis = readNullableLong(), recommendationTitle = readNullableString(),
        recommendationMinutes = readNullableInt(), reasonCode = readBounded()
    )

    private fun DataOutputStream.writeBounded(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size); write(bytes)
    }

    private fun DataInputStream.readBounded(): String {
        val size = readInt()
        if (size !in 0..MAX_STRING_BYTES || size > available()) throw InvalidBackupPayload()
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readCount(): Int = readInt().also {
        if (it !in 0..MAX_ITEMS) throw InvalidBackupPayload()
    }

    private fun DataOutputStream.writeNullableString(value: String?) { writeBoolean(value != null); if (value != null) writeBounded(value) }
    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readBounded() else null
    private fun DataOutputStream.writeNullableInt(value: Int?) { writeBoolean(value != null); value?.let(::writeInt) }
    private fun DataInputStream.readNullableInt(): Int? = if (readBoolean()) readInt() else null
    private fun DataOutputStream.writeNullableLong(value: Long?) { writeBoolean(value != null); value?.let(::writeLong) }
    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null
    private fun DataOutputStream.writeNullableDouble(value: Double?) { writeBoolean(value != null); value?.let(::writeDouble) }
    private fun DataInputStream.readNullableDouble(): Double? = if (readBoolean()) readDouble() else null

    private class InvalidBackupPayload : Exception()
    class UnsupportedBackupVersion : Exception()
}
