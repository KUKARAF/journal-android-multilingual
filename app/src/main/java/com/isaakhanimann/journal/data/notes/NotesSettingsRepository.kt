/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.data.notes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists the notes-sync settings: the server base URL and the "sync to notes server" flag live in
 * the shared DataStore `user_preferences`, while the bearer token is kept in the encrypted
 * [NotesTokenStore]. Implements [NotesConfig] so [NotesClient] can read the current base URL/token.
 */
@Singleton
class NotesSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val tokenStore: NotesTokenStore
) : NotesConfig {

    private object Keys {
        val BASE_URL = stringPreferencesKey("key_notes_base_url")
        val SYNC_ENABLED = booleanPreferencesKey("key_notes_sync_enabled")
    }

    val baseUrlFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.BASE_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_NOTES_BASE_URL
    }

    val isSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SYNC_ENABLED] ?: false
    }

    private val _hasToken = MutableStateFlow(false)

    /** Reactive "a bearer token is stored" state for the settings UI. */
    val hasTokenFlow: StateFlow<Boolean> = _hasToken

    /** Loads the token presence from the encrypted store (call from the UI, on Android). */
    fun refreshTokenState() {
        _hasToken.value = tokenStore.getToken() != null
    }

    suspend fun saveBaseUrl(url: String) {
        dataStore.edit { preferences ->
            val trimmed = url.trim()
            if (trimmed.isBlank()) {
                preferences.remove(Keys.BASE_URL)
            } else {
                preferences[Keys.BASE_URL] = trimmed
            }
        }
    }

    suspend fun saveSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SYNC_ENABLED] = enabled
        }
    }

    fun setToken(token: String?) {
        tokenStore.setToken(token)
        _hasToken.value = token?.isNotBlank() == true
    }

    override suspend fun baseUrl(): String {
        val preferences = dataStore.data.first()
        return preferences[Keys.BASE_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_NOTES_BASE_URL
    }

    override suspend fun bearerToken(): String? = tokenStore.getToken()

    suspend fun isSyncEnabled(): Boolean = dataStore.data.first()[Keys.SYNC_ENABLED] ?: false
}
