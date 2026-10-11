package de.run.app.domain.model

import java.time.LocalDate

data class TrainingDecisionSnapshot(
    val ruleVersion: Int,
    val inputRevision: String,
    val targetRevision: String?,
    val evaluatedOn: LocalDate,
    val runCount: Int,
    val latestRunEndedAtMillis: Long?,
    val recommendationTitle: String?,
    val recommendationMinutes: Int?,
    val reasonCode: String
)
