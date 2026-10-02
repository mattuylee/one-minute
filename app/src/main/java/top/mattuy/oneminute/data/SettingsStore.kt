package top.mattuy.oneminute.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "waiting_rules")

data class WaitingSettings(val packages: Set<String> = emptySet(), val seconds: Int = 60)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private val packagesKey = stringSetPreferencesKey("selected_packages")
    private val secondsKey = intPreferencesKey("waiting_seconds")
    val settings = store.data.map { prefs ->
        WaitingSettings(prefs[packagesKey] ?: emptySet(), (prefs[secondsKey] ?: 60).coerceIn(1, 600))
    }
    suspend fun setSelected(packageName: String, selected: Boolean) = store.edit { prefs ->
        val current = prefs[packagesKey] ?: emptySet()
        prefs[packagesKey] = if (selected) current + packageName else current - packageName
    }
    suspend fun setSeconds(seconds: Int) {
        require(seconds in 1..600)
        store.edit { it[secondsKey] = seconds }
    }
}
