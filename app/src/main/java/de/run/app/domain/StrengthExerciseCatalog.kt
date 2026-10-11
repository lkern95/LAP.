package de.run.app.domain

import de.run.app.domain.model.StrengthExercise

object StrengthExerciseCatalog {
    val exercises = listOf(
        StrengthExercise(
            id = "chair-squat",
            name = "Aufstehen vom Stuhl",
            focus = "Knie und Oberschenkel",
            instruction = "Stell die Füße hüftbreit auf. Steh ohne Schwung auf und setz dich langsam wieder hin. Bei Bedarf mit den Händen abstützen.",
            dosage = "2 Durchgänge mit je 8 Wiederholungen"
        ),
        StrengthExercise(
            id = "calf-raise",
            name = "Wadenheben",
            focus = "Waden und Sprunggelenke",
            instruction = "Halte dich an einer stabilen Lehne fest. Hebe die Fersen langsam an und senke sie kontrolliert wieder ab.",
            dosage = "2 Durchgänge mit je 10 Wiederholungen"
        ),
        StrengthExercise(
            id = "single-leg-balance",
            name = "Einbeinstand",
            focus = "Gleichgewicht und Sprunggelenke",
            instruction = "Steh nahe einer Wand. Hebe einen Fuß leicht an und halte das Becken ruhig. Nutze die Wand bei Bedarf.",
            dosage = "2 Durchgänge mit je 20 Sekunden pro Seite"
        ),
        StrengthExercise(
            id = "glute-bridge",
            name = "Beckenheben",
            focus = "Gesäß und Hüfte",
            instruction = "Leg dich auf den Rücken, stell die Füße auf und hebe das Becken nur so weit, wie es sich angenehm anfühlt. Langsam absenken.",
            dosage = "2 Durchgänge mit je 8 Wiederholungen"
        ),
        StrengthExercise(
            id = "bird-dog",
            name = "Diagonalstrecken im Vierfüßlerstand",
            focus = "Rumpf und Rücken",
            instruction = "Streck einen Arm und das gegenüberliegende Bein nur so weit, wie du den Rücken ruhig halten kannst. Seite wechseln.",
            dosage = "2 Durchgänge mit je 6 Wiederholungen pro Seite"
        )
    )
}
