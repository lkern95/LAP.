package de.run.app.domain.model

data class ReadinessAssessment(
    val score: Int?,
    val sleepScore: Int? = null,
    val autonomicScore: Int? = null,
    val autonomicSignal: String? = null,
    val trainingLoadScore: Int? = null
)
