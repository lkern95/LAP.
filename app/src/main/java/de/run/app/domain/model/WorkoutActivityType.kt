package de.run.app.domain.model

enum class WorkoutActivityType(val label: String) {
    RUNNING("Lauf"),
    WALKING("Gehen"),
    CYCLING("Radfahren"),
    STRENGTH("Krafttraining"),
    OTHER("Aktivität");

    companion object {
        fun fromStorage(value: String): WorkoutActivityType =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
