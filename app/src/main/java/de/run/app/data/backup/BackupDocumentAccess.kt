package de.run.app.data.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.run.app.data.local.appearanceSettings
import de.run.app.data.local.trainingSettings
import java.util.Base64
import kotlinx.coroutines.flow.first

interface BackupDocumentAccess {
    fun read(uri: String, maximumBytes: Int): ByteArray
    fun write(uri: String, bytes: ByteArray)
}

class SafBackupDocumentAccess(context: Context) : BackupDocumentAccess {
    private val resolver = context.contentResolver

    override fun read(uri: String, maximumBytes: Int): ByteArray {
        val stream = resolver.openInputStream(uri.toUri()) ?: throw IllegalStateException()
        return stream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maximumBytes) throw IllegalStateException()
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    override fun write(uri: String, bytes: ByteArray) {
        val output = resolver.openOutputStream(uri.toUri(), "wt") ?: throw IllegalStateException()
        output.use { it.write(bytes) }
    }
}

interface BackupPreferencesAccess {
    suspend fun read(): Map<String, List<BackupPreference>>
    suspend fun replace(stores: Map<String, List<BackupPreference>>)
}

class AndroidBackupPreferencesAccess(private val context: Context) : BackupPreferencesAccess {
    override suspend fun read(): Map<String, List<BackupPreference>> = mapOf(
        "training_settings" to context.trainingSettings.data.first().backupValues(),
        "appearance_settings" to context.appearanceSettings.data.first().backupValues()
    )

    override suspend fun replace(stores: Map<String, List<BackupPreference>>) {
        context.trainingSettings.edit { it.replaceValues(stores.getValue("training_settings")) }
        context.appearanceSettings.edit { it.replaceValues(stores.getValue("appearance_settings")) }
    }

    private fun Preferences.backupValues(): List<BackupPreference> = asMap().map { (key, value) ->
        val (type, encoded) = when (value) {
            is String -> "string" to value
            is Int -> "int" to value.toString()
            is Long -> "long" to value.toString()
            is Float -> "float" to value.toString()
            is Double -> "double" to value.toString()
            is Boolean -> "boolean" to value.toString()
            is Set<*> -> {
                val strings = value.filterIsInstance<String>()
                if (strings.size != value.size) throw IllegalStateException()
                "string_set" to strings.sorted().joinToString(",") {
                    "x" + Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
                }
            }
            else -> throw IllegalStateException()
        }
        BackupPreference(key.name, type, encoded)
    }

    private fun MutablePreferences.replaceValues(values: List<BackupPreference>) {
        clear()
        values.forEach { item ->
            when (item.type) {
                "string" -> this[stringPreferencesKey(item.name)] = item.value
                "int" -> this[intPreferencesKey(item.name)] = item.value.toInt()
                "long" -> this[longPreferencesKey(item.name)] = item.value.toLong()
                "float" -> this[floatPreferencesKey(item.name)] = item.value.toFloat()
                "double" -> this[doublePreferencesKey(item.name)] = item.value.toDouble()
                "boolean" -> this[booleanPreferencesKey(item.name)] = item.value.toBooleanStrict()
                "string_set" -> this[stringSetPreferencesKey(item.name)] = item.value.split(',')
                    .filter(String::isNotEmpty).map {
                        Base64.getDecoder().decode(it.removePrefix("x")).toString(Charsets.UTF_8)
                    }.toSet()
                else -> throw IllegalStateException()
            }
        }
    }
}
