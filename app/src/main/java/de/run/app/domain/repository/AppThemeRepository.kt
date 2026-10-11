package de.run.app.domain.repository

import de.run.app.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow

interface AppThemeRepository {
    fun observe(): Flow<AppTheme>
    suspend fun save(theme: AppTheme)
}
