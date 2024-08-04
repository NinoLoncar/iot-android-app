package foi.nloncar.IoTAndroidApp.managers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

class DataStoreManager(context: Context) {

    private val dataStore = context.dataStore
    private val preferencesKey = stringPreferencesKey("AUTHENTICATION_KEY")
    suspend fun updateAuthenticationKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[preferencesKey] = apiKey
        }
    }

    fun getAuthenticationKey(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[preferencesKey]
        }
    }
}