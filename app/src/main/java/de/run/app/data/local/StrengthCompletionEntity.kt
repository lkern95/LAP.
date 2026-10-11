package de.run.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.run.app.domain.model.ExerciseCompletion
import java.time.LocalDate

@Entity(tableName = "strength_completions")
data class StrengthCompletionEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val completedOn: String
) {
    fun toDomain() = ExerciseCompletion(id, exerciseId, LocalDate.parse(completedOn))

    companion object {
        fun fromDomain(completion: ExerciseCompletion) = StrengthCompletionEntity(
            completion.id,
            completion.exerciseId,
            completion.completedOn.toString()
        )
    }
}
