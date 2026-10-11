package de.run.app.presentation.history

import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.repository.WorkoutRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful empty import records check time and complete history`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = WorkoutHistoryViewModel(FakeWorkoutRepository(true))

        viewModel.refresh()
        runCurrent()

        assertNotNull(viewModel.state.value.lastSuccessfulReadAt)
        assertEquals(true, viewModel.state.value.hasFullHistoryAccess)
        assertEquals(false, viewModel.state.value.hasError)
    }

    @Test
    fun `limited history access remains explicit after a successful import`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = WorkoutHistoryViewModel(FakeWorkoutRepository(false))

        viewModel.refresh()
        runCurrent()

        assertNotNull(viewModel.state.value.lastSuccessfulReadAt)
        assertEquals(false, viewModel.state.value.hasFullHistoryAccess)
        assertEquals(false, viewModel.state.value.hasError)
    }

    private class FakeWorkoutRepository(private val fullHistory: Boolean) : WorkoutRepository {
        private val records = MutableStateFlow(emptyList<Workout>())

        override fun observeRecent(): Flow<List<Workout>> = records

        override suspend fun refresh(from: Instant, to: Instant, readInBackground: Boolean): WorkoutReadResult =
            WorkoutReadResult.Available(emptyList(), hasFullHistoryAccess = fullHistory)

        override suspend fun saveFeedback(sourceId: String, feedback: WorkoutFeedback) = false
    }
}
