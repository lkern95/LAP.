package de.run.app.presentation.plan

internal enum class GarminConnectionUiState {
    IDLE,
    CONNECTING,
    CONNECTED,
    FAILED
}

internal fun resolveGarminConnectionUiState(
    requested: Boolean,
    resultArrived: Boolean,
    loading: Boolean,
    connected: Boolean
): GarminConnectionUiState = when {
    !requested -> GarminConnectionUiState.IDLE
    loading || !resultArrived -> GarminConnectionUiState.CONNECTING
    connected -> GarminConnectionUiState.CONNECTED
    else -> GarminConnectionUiState.FAILED
}
