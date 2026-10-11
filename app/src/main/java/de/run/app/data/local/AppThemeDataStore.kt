package de.run.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.run.app.domain.model.AppTheme
import de.run.app.domain.repository.AppThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.appearanceSettings by preferencesDataStore(name = "appearance_settings")

class AppThemeDataStore(private val context: Context) : AppThemeRepository {
    override fun observe(): Flow<AppTheme> = context.appearanceSettings.data.map { values ->
        values[THEME]?.let { saved -> AppTheme.entries.firstOrNull { it.name == saved } }
            ?: AppTheme.OLIVE
    }

    override suspend fun save(theme: AppTheme) {
        context.appearanceSettings.edit { values -> values[THEME] = theme.name }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
    }
}
