package de.run.app.domain.model

enum class ReadinessGuidanceKind {
    LOADING,
    ACCESS_NEEDED,
    UNAVAILABLE,
    FAILED,
    NO_RUNS,
    NO_PLAN,
    WEEK_COMPLETE,
    FOLLOW_PLAN
}

data class ReadinessGuidance(
    val kind: ReadinessGuidanceKind,
    val completedRuns: Int = 0,
    val plannedRuns: Int = 0
)
