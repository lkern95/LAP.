package de.run.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.run.app.domain.model.TrainingDecisionSnapshot
import java.time.LocalDate

@Entity(tableName = "training_decisions")
data class TrainingDecisionEntity(
    @PrimaryKey val id: String,
    val ruleVersion: Int,
    val inputRevision: String,
    val targetRevision: String?,
    val evaluatedOn: String,
    val runCount: Int,
    val latestRunEndedAtMillis: Long?,
    val recommendationTitle: String?,
    val recommendationMinutes: Int?,
    val reasonCode: String
) {
    fun toDomain() = TrainingDecisionSnapshot(
        ruleVersion,
        inputRevision,
        targetRevision,
        LocalDate.parse(evaluatedOn),
        runCount,
        latestRunEndedAtMillis,
        recommendationTitle,
        recommendationMinutes,
        reasonCode
    )

    companion object {
        fun fromDomain(snapshot: TrainingDecisionSnapshot) = TrainingDecisionEntity(
            id = CURRENT_ID,
            ruleVersion = snapshot.ruleVersion,
            inputRevision = snapshot.inputRevision,
            targetRevision = snapshot.targetRevision,
            evaluatedOn = snapshot.evaluatedOn.toString(),
            runCount = snapshot.runCount,
            latestRunEndedAtMillis = snapshot.latestRunEndedAtMillis,
            recommendationTitle = snapshot.recommendationTitle,
            recommendationMinutes = snapshot.recommendationMinutes,
            reasonCode = snapshot.reasonCode
        )

        const val CURRENT_ID = "current"
    }
}
