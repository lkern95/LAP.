package de.run.app.application

import de.run.app.domain.TrainingFeedback
import de.run.app.domain.TrainingFeedbackEngine
import de.run.app.domain.TrainingPlanBuilder
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import java.time.LocalDate
import java.time.ZoneId

/** Combines the saved goal, plan, and actual activity into one deterministic feedback result. */
class EvaluateWorkoutTrainingFeedback(
    private val planBuilder: TrainingPlanBuilder,
    private val feedbackEngine: TrainingFeedbackEngine = TrainingFeedbackEngine()
) {
    operator fun invoke(
        workout: Workout,
        workouts: List<Workout>,
        profile: TrainingProfile,
        goal: TrainingGoal?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): TrainingFeedback {
        val plan = planBuilder.build(profile, goal, today, workouts)
        return feedbackEngine.analyze(workout, workouts, goal, plan, today, zone, profile)
    }
}
