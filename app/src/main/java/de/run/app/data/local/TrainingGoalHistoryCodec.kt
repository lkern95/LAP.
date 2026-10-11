package de.run.app.data.local

import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64

internal sealed interface GoalHistoryDecodeResult {
    data class Valid(val goals: List<TrainingGoal>, val needsVersionUpgrade: Boolean) : GoalHistoryDecodeResult
    data object Invalid : GoalHistoryDecodeResult
}

internal object TrainingGoalHistoryCodec {
    private const val VERSION_PREFIX = "v1:"
    private const val FIELD_COUNT = 11

    fun decode(raw: String): GoalHistoryDecodeResult {
        val versioned = raw.startsWith("v") && raw.contains(':')
        if (versioned && !raw.startsWith(VERSION_PREFIX)) return GoalHistoryDecodeResult.Invalid
        val payload = if (raw.startsWith(VERSION_PREFIX)) raw.removePrefix(VERSION_PREFIX) else raw
        if (payload.isEmpty()) return GoalHistoryDecodeResult.Valid(emptyList(), !raw.startsWith(VERSION_PREFIX))

        val rows = payload.split(';')
        val decoded = rows.map { row -> decodeRow(row) ?: return GoalHistoryDecodeResult.Invalid }
        if (decoded.map(TrainingGoal::id).toSet().size != decoded.size) return GoalHistoryDecodeResult.Invalid
        if (decoded.count { it.status == TrainingGoalStatus.ACTIVE } > 1) return GoalHistoryDecodeResult.Invalid
        return GoalHistoryDecodeResult.Valid(decoded, !raw.startsWith(VERSION_PREFIX))
    }

    fun encode(goals: List<TrainingGoal>): String = VERSION_PREFIX + goals.joinToString(";") { goal ->
        listOf(
            encodeField(goal.id), goal.raceDate.toString(), goal.raceDistanceMeters.toString(), goal.raceGoal.name,
            goal.targetTimeMinutes?.toString() ?: "-", goal.status.name,
            goal.competitionCompleted?.toString() ?: "-", goal.goalAchieved?.toString() ?: "-",
            goal.competitionWorkoutId?.let(::encodeField) ?: "-",
            goal.pendingCompetitionWorkoutId?.let(::encodeField) ?: "-",
            goal.lastPromptedWorkoutId?.let(::encodeField) ?: "-"
        ).joinToString("|")
    }

    private fun decodeRow(row: String): TrainingGoal? {
        return try {
            val fields = row.split('|')
            if (fields.size != FIELD_COUNT) return null
            val id = decodeField(fields[0])?.takeIf(String::isNotBlank) ?: return null
            val distance = fields[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val targetTime = nullableInt(fields[4]) ?: if (fields[4] == "-") null else return null
            val completed = nullableBoolean(fields[6]) ?: if (fields[6] == "-") null else return null
            val achieved = nullableBoolean(fields[7]) ?: if (fields[7] == "-") null else return null
            TrainingGoal(
                id = id,
                raceDate = LocalDate.parse(fields[1]),
                raceDistanceMeters = distance,
                raceGoal = RaceGoal.valueOf(fields[3]),
                targetTimeMinutes = targetTime,
                status = TrainingGoalStatus.valueOf(fields[5]),
                competitionCompleted = completed,
                goalAchieved = achieved,
                competitionWorkoutId = nullableDecoded(fields[8]) ?: if (fields[8] == "-") null else return null,
                pendingCompetitionWorkoutId = nullableDecoded(fields[9]) ?: if (fields[9] == "-") null else return null,
                lastPromptedWorkoutId = nullableDecoded(fields[10]) ?: if (fields[10] == "-") null else return null
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun nullableInt(value: String): Int? = value.takeUnless { it == "-" }?.toIntOrNull()
    private fun nullableBoolean(value: String): Boolean? = when (value) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun nullableDecoded(value: String): String? = value.takeUnless { it == "-" }?.let(::decodeField)

    private fun encodeField(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String? = try {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
