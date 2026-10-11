package de.run.app.presentation.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class GarminConnectionUiStateTest {
    @Test
    fun notRequestedDoesNotShowStatus() {
        assertEquals(
            GarminConnectionUiState.IDLE,
            resolveGarminConnectionUiState(false, false, false, false)
        )
    }

    @Test
    fun waitingForPermissionResultKeepsAnimationRunning() {
        assertEquals(
            GarminConnectionUiState.CONNECTING,
            resolveGarminConnectionUiState(true, false, false, false)
        )
    }

    @Test
    fun permissionGrantedAndDataReadyShowsSuccess() {
        assertEquals(
            GarminConnectionUiState.CONNECTED,
            resolveGarminConnectionUiState(true, true, false, true)
        )
    }

    @Test
    fun completedRequestWithoutConnectionShowsFailureInsteadOfEndlessLoading() {
        assertEquals(
            GarminConnectionUiState.FAILED,
            resolveGarminConnectionUiState(true, true, false, false)
        )
    }
}
