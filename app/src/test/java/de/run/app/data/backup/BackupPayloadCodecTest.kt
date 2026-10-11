package de.run.app.data.backup

import de.run.app.data.local.StrengthCompletionEntity
import de.run.app.data.local.TrainingDecisionEntity
import de.run.app.data.local.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupPayloadCodecTest {
    @Test
    fun currentFormatKeepsEveryAppOwnedRecordAndPreferenceType() {
        val snapshot = sampleSnapshot()

        val decoded = BackupPayloadCodec.decode(BackupPayloadCodec.encode(snapshot), BackupPayloadCodec.CURRENT_VERSION)

        assertEquals(snapshot, decoded)
    }

    @Test
    fun legacyFormatRestoresOlderRunsWithoutFeedbackAndWithoutDecisionHistory() {
        val decoded = BackupPayloadCodec.decode(legacyV1(), 1)

        assertEquals("legacy-run", decoded.workouts.single().sourceId)
        assertNull(decoded.workouts.single().feedbackRecordedAtMillis)
        assertEquals(emptyList<TrainingDecisionEntity>(), decoded.trainingDecisions)
        assertEquals("v1:goal", decoded.stores.getValue("training_settings").single().value)
    }

    @Test
    fun rejectsUnsupportedAndTruncatedPayloads() {
        assertThrows(BackupPayloadCodec.UnsupportedBackupVersion::class.java) {
            BackupPayloadCodec.decode(byteArrayOf(), 99)
        }
        assertThrows(Exception::class.java) {
            BackupPayloadCodec.decode(byteArrayOf(0, 0, 0, 2, 0), 2)
        }
    }

    private fun legacyV1() = java.io.ByteArrayOutputStream().use { bytes ->
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(1)
            listOf("legacy-run", "Garmin Connect", "Lauf").forEach { out.writeLegacyString(it) }
            out.writeLong(1_000L); out.writeLong(2_000L)
            out.writeBoolean(true); out.writeDouble(5_000.0)
            out.writeBoolean(false); out.writeBoolean(true); out.writeInt(140)
            out.writeInt(0) // strength sessions
            out.writeInt(1) // settings stores
            out.writeLegacyString("training_settings")
            out.writeInt(1)
            out.writeLegacyString("training_goal_history_v1")
            out.writeLegacyString("string")
            out.writeLegacyString("v1:goal")
        }
        bytes.toByteArray()
    }

    private fun java.io.DataOutputStream.writeLegacyString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun sampleSnapshot() = BackupSnapshot(
        workouts = listOf(
            WorkoutEntity("g-1", "Garmin Connect", "Lauf", 1, 2, 5_000.0, 142, 167, 3, "NONE", 4)
        ),
        strengthCompletions = listOf(StrengthCompletionEntity("s-1", "knee", "2026-10-10")),
        trainingDecisions = listOf(TrainingDecisionEntity("current", 2, "input", "goal", "2026-10-11", 3, 2, "Locker", 30, "READY")),
        stores = mapOf(
            "training_settings" to listOf(BackupPreference("event_date", "string", "2027-03-21")),
            "appearance_settings" to listOf(BackupPreference("theme", "string", "DARK"))
        )
    )
}
