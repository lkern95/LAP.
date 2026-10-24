package de.run.app.data.strength

import java.io.File
import de.run.app.domain.model.StrengthPrescription
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StrengthCatalogJsonParserTest {
    private val parser = StrengthCatalogJsonParser()
    private val source by lazy { File("src/main/assets/strength/runner_training_catalog_v2.json").readText() }

    @Test
    fun providedCatalogLoadsAllProgramsAndExercises() {
        val catalog = parser.parse(source)
        assertEquals(12, catalog.exercises.size)
        assertEquals(3, catalog.workouts.size)
        assertEquals(StrengthPrescription.Type.HOLD, catalog.exercisesById.getValue("wandsitz_90_90").prescription.type)
        assertEquals("glute_bridge", catalog.exercisesById.getValue("glute_bridge").media.imageKey)
        assertEquals("adduktoren_rockback_dehnung", catalog.workouts.last().optionalExerciseIds.single())
    }

    @Test
    fun rejectsUnsupportedVersionDuplicateIdsAndUnknownExerciseReferences() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(JSONObject(source).put("schemaVersion", "9.0.0").toString())
        }
        val duplicate = JSONObject(source)
        duplicate.getJSONArray("exercises").put(duplicate.getJSONArray("exercises").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { parser.parse(duplicate.toString()) }
        val missingReference = JSONObject(source)
        missingReference.getJSONArray("workouts").getJSONObject(0).getJSONArray("exerciseIds").put("unknown_exercise")
        assertThrows(IllegalArgumentException::class.java) { parser.parse(missingReference.toString()) }
    }

    @Test
    fun addingAValidExerciseDoesNotRequireUiChanges() {
        val root = JSONObject(source)
        val copy = JSONObject(root.getJSONArray("exercises").getJSONObject(0).toString())
            .put("id", "new_local_exercise")
            .put("title", "Neue lokale Übung")
            .put("media", JSONObject(root.getJSONArray("exercises").getJSONObject(0).getJSONObject("media").toString()).put("imageKey", "new_local_exercise"))
        root.getJSONArray("exercises").put(copy)
        assertEquals("Neue lokale Übung", parser.parse(root.toString()).exercisesById.getValue("new_local_exercise").title)
    }
}
