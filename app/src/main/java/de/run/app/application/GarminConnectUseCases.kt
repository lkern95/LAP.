package de.run.app.application

import de.run.app.domain.repository.GarminConnectRepository
import java.time.LocalDateTime

class ConnectGarminConnect(private val repository: GarminConnectRepository) {
    suspend operator fun invoke(email: String, password: CharArray) = repository.connect(email, password)
}

class SubmitGarminConnectMfa(private val repository: GarminConnectRepository) {
    suspend operator fun invoke(code: CharArray) = repository.submitMfaCode(code)
}

class SyncGarminConnectActivity(private val repository: GarminConnectRepository) {
    suspend operator fun invoke(target: LocalDateTime) = repository.syncActivityNear(target)
}

class SyncAllGarminConnectActivities(private val repository: GarminConnectRepository) {
    suspend operator fun invoke() = repository.syncAllActivities()
}

class DisconnectGarminConnect(private val repository: GarminConnectRepository) {
    suspend operator fun invoke() = repository.disconnect()
}
