package de.run.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.repository.TrainingGoalHistoryUnavailableException
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingGoalDataStoreTest {
    @Test
    fun `valid legacy history upgrades without losing entries`() = runTest {
        val history = listOf(goal("first"), goal("second", TrainingGoalStatus.COMPLETED))
        val store = createStore()
        store.raw.edit { it[historyKey] = history.joinToString(";") { TrainingGoalHistoryCodec.encode(listOf(it)).removePrefix("v1:") } }

        assertEquals(history, store.repository.observeHistory().first())
        assertEquals(history, store.repository.observeHistory().first())
        assertTrue(store.raw.data.first()[historyKey].orEmpty().startsWith("v1:"))
    }

    @Test
    fun `empty history is a valid empty result`() = runTest {
        val store = createStore()
        store.raw.edit { it[historyKey] = "" }

        assertTrue(store.repository.observeHistory().first().isEmpty())
        assertEquals("v1:", store.raw.data.first()[historyKey])
    }

    @Test
    fun `damaged mixed record fails closed and remains unchanged`() = runTest {
        val store = createStore()
        val valid = TrainingGoalHistoryCodec.encode(listOf(goal("kept"))).removePrefix("v1:")
        val damaged = "$valid;not-a-goal"
        store.raw.edit { it[historyKey] = damaged }

        assertHistoryReadFails(store)
        assertGoalSaveFails(store, goal("new"))
        assertEquals(damaged, store.raw.data.first()[historyKey])
    }

    @Test
    fun `truncated and unknown version formats fail closed`() = runTest {
        val store = createStore()
        for (damaged in listOf("v1:YWJj|2026-10-10", "v9:future-format")) {
            store.raw.edit { it[historyKey] = damaged }
            assertHistoryReadFails(store)
            assertEquals(damaged, store.raw.data.first()[historyKey])
        }
    }

    @Test
    fun `repeated save and update keep one active goal and preserve history`() = runTest {
        val store = createStore()
        val first = goal("first")
        val second = goal("second")
        store.repository.saveAsCurrent(first)
        store.repository.saveAsCurrent(second)
        store.repository.update(second.copy(raceDate = LocalDate.of(2027, 3, 21)))
        store.repository.saveAsCurrent(second.copy(raceDate = LocalDate.of(2027, 3, 21)))

        val history = store.repository.observeHistory().first()
        assertEquals(listOf(TrainingGoalStatus.REPLACED, TrainingGoalStatus.ACTIVE), history.map { it.status })
        assertEquals(listOf("first", "second"), history.map { it.id })
        assertEquals(LocalDate.of(2027, 3, 21), history.last().raceDate)
    }

    @Test
    fun `legacy scalar profile goal migrates once with stable identity`() = runTest {
        val store = createStore()
        store.raw.edit {
            it[eventDateKey] = "2027-03-21"
            it[distanceKey] = 21_100
            it[raceGoalKey] = RaceGoal.RUN_THROUGH.name
            it[targetTimeKey] = 180
        }

        val migrated = store.repository.observeHistory().first()
        val repeated = store.repository.observeHistory().first()
        assertEquals(1, migrated.size)
        assertEquals(migrated, repeated)
        assertEquals(21_100, migrated.single().raceDistanceMeters)
        assertEquals(RaceGoal.RUN_THROUGH, migrated.single().raceGoal)
        assertEquals(180, migrated.single().targetTimeMinutes)
    }

    @Test
    fun `empty legacy history recovers still present scalar goal`() = runTest {
        val store = createStore()
        store.raw.edit {
            it[historyKey] = ""
            it[eventDateKey] = "2027-03-21"
        }

        val migrated = store.repository.observeHistory().first()
        assertEquals(1, migrated.size)
        assertEquals(LocalDate.of(2027, 3, 21), migrated.single().raceDate)
        assertTrue(store.raw.data.first()[historyKey].orEmpty().startsWith("v1:"))
    }

    @Test
    fun `new goal keeps completed goal records`() = runTest {
        val store = createStore()
        val completed = goal("completed", TrainingGoalStatus.COMPLETED)
        store.repository.saveAsCurrent(completed)
        store.repository.update(completed)
        store.repository.saveAsCurrent(goal("new"))

        assertEquals(
            listOf("completed", "new"),
            store.repository.observeHistory().first().map { it.id }
        )
    }

    private suspend fun assertHistoryReadFails(store: TestStore) {
        try {
            store.repository.observeHistory().first()
            fail("Expected history read to fail")
        } catch (_: TrainingGoalHistoryUnavailableException) {
            assertTrue(true)
        }
    }

    private suspend fun assertGoalSaveFails(store: TestStore, goal: TrainingGoal) {
        try {
            store.repository.saveAsCurrent(goal)
            fail("Expected goal save to fail")
        } catch (_: TrainingGoalHistoryUnavailableException) {
            assertTrue(true)
        }
    }

    private fun goal(id: String, status: TrainingGoalStatus = TrainingGoalStatus.ACTIVE) = TrainingGoal(
        id = id,
        raceDate = LocalDate.of(2027, 3, 21),
        raceDistanceMeters = 21_100,
        raceGoal = RaceGoal.ARRIVE,
        status = status
    )

    private fun createStore(): TestStore {
        val raw = MemoryPreferencesDataStore()
        return TestStore(raw, TrainingGoalDataStore(raw))
    }

    private data class TestStore(val raw: MemoryPreferencesDataStore, val repository: TrainingGoalDataStore)

    private class MemoryPreferencesDataStore : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = values

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(values.value)
            values.value = updated
            return updated
        }
    }

    private companion object {
        val historyKey = stringPreferencesKey("training_goal_history_v1")
        val eventDateKey = stringPreferencesKey("event_date")
        val distanceKey = intPreferencesKey("race_distance_meters")
        val raceGoalKey = stringPreferencesKey("race_goal")
        val targetTimeKey = intPreferencesKey("target_time_minutes")
    }
}
