package de.run.app.data.strength

import de.run.app.domain.model.StrengthCatalog
import de.run.app.domain.model.StrengthCatalogExercise
import de.run.app.domain.model.StrengthMedia
import de.run.app.domain.model.StrengthPrescription
import de.run.app.domain.model.StrengthWorkout
import org.json.JSONArray
import org.json.JSONObject

class StrengthCatalogJsonParser {
    fun parse(text: String): StrengthCatalog {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) { "Die Datei ist zu groß." }
        val root = JSONObject(text)
        require(root.getString("schemaVersion") == SUPPORTED_SCHEMA) { "Diese Katalogversion wird nicht unterstützt." }
        require(root.getString("locale") == "de-DE")
        root.requireKeys(setOf("schemaVersion", "locale", "safetyAdvice", "exercises", "workouts"))
        val safetyAdvice = root.getString("safetyAdvice").requiredText()
        val exercises = root.getJSONArray("exercises").map(::parseExercise)
        val workouts = root.getJSONArray("workouts").map(::parseWorkout)
        require(exercises.isNotEmpty() && workouts.isNotEmpty())
        require(exercises.map { it.id }.toSet().size == exercises.size) { "Übungs-IDs müssen eindeutig sein." }
        require(workouts.map { it.id }.toSet().size == workouts.size) { "Programm-IDs müssen eindeutig sein." }
        val exerciseIds = exercises.mapTo(mutableSetOf()) { it.id }
        workouts.forEach { workout ->
            require(workout.exerciseIds.isNotEmpty() && workout.exerciseIds.all(exerciseIds::contains)) {
                "Ein Programm verweist auf eine unbekannte Übung."
            }
            require(workout.optionalExerciseIds.all(workout.exerciseIds::contains))
        }
        return StrengthCatalog(
            schemaVersion = SUPPORTED_SCHEMA,
            safetyAdvice = safetyAdvice,
            exercises = exercises,
            workouts = workouts,
            sourceJson = root.toString(),
            rawExerciseJson = root.getJSONArray("exercises").objectsById(),
            rawWorkoutJson = root.getJSONArray("workouts").objectsById()
        )
    }

    private fun parseExercise(value: JSONObject): StrengthCatalogExercise {
        value.requireKeys(
            setOf("id", "title", "subtitle", "description", "movementType", "difficulty", "focusAreas", "runnerGoals", "equipment", "prescription", "steps", "techniqueCues", "tip", "commonMistakes", "cautions", "regression", "progression", "media"),
            setOf("pnfPattern")
        )
        require(value.getString("movementType") in setOf("dynamic", "isometric", "motor_control", "mobility"))
        require(value.getString("difficulty") in setOf("beginner", "intermediate", "advanced"))
        value.getJSONArray("focusAreas").enumList(FOCUS_AREAS, 1, 10)
        value.getJSONArray("runnerGoals").enumList(RUNNER_GOALS, 1, 12)
        value.getJSONArray("equipment").enumList(EQUIPMENT, 0, 7)
        value.getJSONArray("steps").strings().also { require(it.size in 3..8) }
        value.getJSONArray("techniqueCues").strings().also { require(it.size in 1..6) }
        value.optJSONObject("pnfPattern")?.let(::validatePnfPattern)
        val prescription = value.getJSONObject("prescription")
        val type = when (prescription.getString("type")) {
            "repetitions" -> StrengthPrescription.Type.REPETITIONS
            "hold" -> StrengthPrescription.Type.HOLD
            else -> error("Unbekannte Dosierung.")
        }
        val prescriptionKeys = if (type == StrengthPrescription.Type.REPETITIONS) {
            setOf("type", "reps", "perSide", "restSeconds") to setOf("tempo", "holdPerRepSeconds")
        } else setOf("type", "holdSeconds", "perSide", "restSeconds") to emptySet()
        prescription.requireKeys(prescriptionKeys.first, prescriptionKeys.second)
        val reps = prescription.optNullableInt("reps")
        val holdSeconds = prescription.optNullableInt("holdSeconds")
        val rest = prescription.getInt("restSeconds").also { require(it in 0..300) }
        if (type == StrengthPrescription.Type.REPETITIONS) require(reps in 1..100)
        else require(holdSeconds in 5..300)
        prescription.optNullableInt("holdPerRepSeconds")?.let { require(it in 1..60) }
        prescription.optString("tempo").takeIf(String::isNotBlank)?.let { require(it.matches(TEMPO)) }
        val media = value.getJSONObject("media")
        media.requireKeys(setOf("imageKey", "altText", "illustrationDirection"))
        val imageKey = media.getString("imageKey")
        require(imageKey.matches(IMAGE_KEY)) { "Ungültige Bildkennung." }
        return StrengthCatalogExercise(
            id = value.getString("id").validId(),
            title = value.getString("title").requiredText(),
            subtitle = value.getString("subtitle").requiredText(),
            description = value.getString("description").requiredText(),
            equipment = value.getJSONArray("equipment").strings(),
            prescription = StrengthPrescription(
                type, reps, holdSeconds, prescription.getBoolean("perSide"), rest,
                prescription.optString("tempo").takeIf(String::isNotBlank),
                prescription.optNullableInt("holdPerRepSeconds")
            ),
            steps = value.getJSONArray("steps").strings(),
            techniqueCues = value.getJSONArray("techniqueCues").strings(),
            tip = value.getString("tip").requiredText(),
            commonMistakes = value.getJSONArray("commonMistakes").strings(),
            cautions = value.getJSONArray("cautions").strings(),
            regression = value.getString("regression").requiredText(),
            progression = value.getString("progression").requiredText(),
            media = StrengthMedia(imageKey, media.getString("altText").requiredText(), media.getString("illustrationDirection").requiredText())
        )
    }

    private fun parseWorkout(value: JSONObject): StrengthWorkout {
        value.requireKeys(
            setOf("id", "title", "subtitle", "description", "estimatedMinutes", "warmUpMinutes", "rounds", "restBetweenExercisesSeconds", "restBetweenRoundsSeconds", "runnerGoals", "exerciseIds"),
            setOf("optionalExerciseIds")
        )
        value.getJSONArray("runnerGoals").enumList(RUNNER_GOALS, 1, 12)
        val exerciseIds = value.getJSONArray("exerciseIds").strings().map { it.validId() }
        require(exerciseIds.toSet().size == exerciseIds.size)
        val optional = value.optJSONArray("optionalExerciseIds")?.strings()?.map { it.validId() }?.toSet().orEmpty()
        require(optional.size == (value.optJSONArray("optionalExerciseIds")?.length() ?: 0))
        return StrengthWorkout(
        id = value.getString("id").validId(),
        title = value.getString("title").requiredText(),
        subtitle = value.getString("subtitle").requiredText(),
        description = value.getString("description").requiredText(),
        estimatedMinutes = value.getInt("estimatedMinutes").also { require(it in 1..180) },
        warmUpMinutes = value.getInt("warmUpMinutes").also { require(it in 0..30) },
        rounds = value.getInt("rounds").also { require(it in 1..6) },
        restBetweenExercisesSeconds = value.getInt("restBetweenExercisesSeconds").also { require(it in 0..300) },
        restBetweenRoundsSeconds = value.getInt("restBetweenRoundsSeconds").also { require(it in 0..600) },
        exerciseIds = exerciseIds,
        optionalExerciseIds = optional
    )
    }

    private fun validatePnfPattern(pattern: JSONObject) {
        pattern.requireKeys(setOf("family", "diagonal", "coordinationModel", "phases"))
        require(pattern.getString("family") == "lower_extremity")
        require(pattern.getString("diagonal") in setOf("D1", "D2"))
        require(pattern.getString("coordinationModel") in setOf("sprinter_gait_oriented", "skater_gait_oriented", "isolated_diagonal"))
        val phases = pattern.getJSONArray("phases")
        require(phases.length() == 2)
        repeat(phases.length()) { index ->
            val phase = phases.getJSONObject(index)
            phase.requireKeys(setOf("direction", "hipComponents", "ankleComponents"))
            require(phase.getString("direction") in setOf("flexion", "extension"))
            phase.getJSONArray("hipComponents").enumList(HIP_COMPONENTS, 3, 3)
            phase.getJSONArray("ankleComponents").enumList(ANKLE_COMPONENTS, 1, 4)
        }
    }

    private fun String.requiredText() = trim().also { require(it.isNotEmpty() && it.length <= MAX_TEXT_LENGTH) }
    private fun String.validId() = also { require(matches(ID)) { "Ungültige Kennung." } }
    private fun JSONObject.optNullableInt(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it).requiredText() }
    private fun JSONArray.enumList(allowed: Set<String>, minimum: Int, maximum: Int) {
        val values = strings()
        require(values.size in minimum..maximum && values.all(allowed::contains) && values.toSet().size == values.size)
    }
    private fun JSONObject.requireKeys(required: Set<String>, optional: Set<String> = emptySet()) {
        val keys = keys().asSequence().toSet()
        require(keys.containsAll(required) && (keys - required - optional).isEmpty())
    }
    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> = List(length()) { transform(getJSONObject(it)) }
    private fun JSONArray.objectsById(): Map<String, String> = List(length()) { getJSONObject(it) }
        .associate { it.getString("id") to it.toString() }

    companion object {
        const val SUPPORTED_SCHEMA = "1.1.0"
        const val MAX_CATALOG_BYTES = 1_000_000
        private const val MAX_TEXT_LENGTH = 8_000
        private val ID = Regex("^[a-z0-9_]{1,80}$")
        private val IMAGE_KEY = Regex("^[a-z0-9_]{1,80}$")
        private val TEMPO = Regex("^[0-9Xx]+-[0-9Xx]+-[0-9Xx]+-[0-9Xx]+$")
        private val FOCUS_AREAS = setOf("quadriceps", "gluteus_maximus", "hip_abductors", "hip_external_rotators", "hamstrings", "soleus", "abdominal_control", "knee_proprioception", "ankle_stabilizers", "hip_adductors")
        private val RUNNER_GOALS = setOf("running_strength", "uphill", "downhill", "knee_tracking", "knee_hyperextension_control", "hip_stability", "lumbopelvic_control", "ankle_stability", "gait_coordination", "hip_abductor_strength", "hip_extension_strength", "adductor_mobility")
        private val EQUIPMENT = setOf("mini_band", "long_band", "wall", "step", "stable_band_anchor", "optional_support", "foot_cuff")
        private val HIP_COMPONENTS = setOf("flexion", "extension", "abduction", "adduction", "internal_rotation", "external_rotation")
        private val ANKLE_COMPONENTS = setOf("dorsiflexion", "plantarflexion", "inversion", "eversion")
    }
}
