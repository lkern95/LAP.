package de.run.app.domain.repository

import de.run.app.domain.model.ReadinessHealthSnapshot
import java.time.Instant

interface ReadinessHealthRepository {
    suspend fun read(from: Instant, to: Instant): ReadinessHealthSnapshot
}
