package com.yourapp.wlm.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.wlm.data.local.datastore.SessionDataStore.Companion.SESSION_DATASTORE_NAME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SESSION_DATASTORE_NAME
)

@Singleton
class SessionDataStore @Inject constructor(
    private val context: Context
) {
    companion object {
        const val SESSION_DATASTORE_NAME = "wlm_session_prefs"

        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_PASSPORT_TOKEN = stringPreferencesKey("passport_token")
        private val KEY_DA_TOKEN = stringPreferencesKey("da_token")
        private val KEY_NS_SESSION_ID = stringPreferencesKey("ns_session_id")
        private val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me")
        private val KEY_LOGGED_IN = booleanPreferencesKey("logged_in")
        private val KEY_TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_PERSONAL_MESSAGE = stringPreferencesKey("personal_message")
        private val KEY_USER_STATUS = stringPreferencesKey("user_status")
    }

    val isLoggedIn: Flow<Boolean> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_LOGGED_IN] ?: false
    }

    val savedEmail: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_EMAIL] ?: ""
    }

    val savedToken: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_PASSPORT_TOKEN] ?: ""
    }

    val savedDaToken: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_DA_TOKEN] ?: ""
    }

    val savedDisplayName: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_DISPLAY_NAME] ?: ""
    }

    val shouldRememberMe: Flow<Boolean> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_REMEMBER_ME] ?: false
    }

    val userEmail: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_USER_EMAIL] ?: ""
    }

    val personalMessage: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_PERSONAL_MESSAGE] ?: ""
    }

    val userStatus: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[KEY_USER_STATUS] ?: "NLN"
    }

    suspend fun saveLoginSession(
        email: String,
        displayName: String,
        passportToken: String,
        daToken: String,
        rememberMe: Boolean
    ) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_EMAIL] = email
            prefs[KEY_DISPLAY_NAME] = displayName
            prefs[KEY_PASSPORT_TOKEN] = passportToken
            prefs[KEY_DA_TOKEN] = daToken
            prefs[KEY_REMEMBER_ME] = rememberMe
            prefs[KEY_LOGGED_IN] = true
            prefs[KEY_TOKEN_EXPIRY] = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
        }
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_LOGGED_IN] = loggedIn
        }
    }

    suspend fun saveUserProfile(email: String, displayName: String, personalMessage: String, status: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_DISPLAY_NAME] = displayName
            prefs[KEY_PERSONAL_MESSAGE] = personalMessage
            prefs[KEY_USER_STATUS] = status
        }
    }

    suspend fun saveNsSession(sessionId: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_NS_SESSION_ID] = sessionId
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_DISPLAY_NAME)
            prefs.remove(KEY_PASSPORT_TOKEN)
            prefs.remove(KEY_DA_TOKEN)
            prefs.remove(KEY_NS_SESSION_ID)
            prefs.remove(KEY_REMEMBER_ME)
            prefs.remove(KEY_LOGGED_IN)
            prefs.remove(KEY_TOKEN_EXPIRY)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_PERSONAL_MESSAGE)
            prefs.remove(KEY_USER_STATUS)
        }
    }
}
