package com.example.autoglmagent.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.autoglmagent.agent.ModelSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "autoglm_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val model = stringPreferencesKey("model")
        val apiKey = stringPreferencesKey("api_key")
        val maxSteps = intPreferencesKey("max_steps")
    }

    val settings: Flow<ModelSettings> = context.settingsStore.data.map { prefs ->
        ModelSettings(
            baseUrl = prefs[Keys.baseUrl] ?: "https://open.bigmodel.cn/api/paas/v4",
            model = prefs[Keys.model] ?: "autoglm-phone",
            apiKey = prefs[Keys.apiKey] ?: "",
            maxSteps = prefs[Keys.maxSteps] ?: 12,
        )
    }

    suspend fun save(settings: ModelSettings) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.baseUrl] = settings.baseUrl.trim().trimEnd('/')
            prefs[Keys.model] = settings.model.trim()
            prefs[Keys.apiKey] = settings.apiKey.trim()
            prefs[Keys.maxSteps] = settings.maxSteps.coerceIn(1, 50)
        }
    }
}
