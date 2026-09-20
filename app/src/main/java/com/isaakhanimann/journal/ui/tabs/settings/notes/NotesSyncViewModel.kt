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

package com.isaakhanimann.journal.ui.tabs.settings.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.notes.DEFAULT_NOTES_BASE_URL
import com.isaakhanimann.journal.data.notes.NotesSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotesSyncViewModel @Inject constructor(
    private val notesSettings: NotesSettingsRepository
) : ViewModel() {

    val baseUrlFlow = notesSettings.baseUrlFlow.stateIn(
        initialValue = DEFAULT_NOTES_BASE_URL,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val isSyncEnabledFlow = notesSettings.isSyncEnabledFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val hasTokenFlow = notesSettings.hasTokenFlow

    init {
        notesSettings.refreshTokenState()
    }

    fun saveBaseUrl(url: String) = viewModelScope.launch {
        notesSettings.saveBaseUrl(url)
    }

    fun saveSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        notesSettings.saveSyncEnabled(enabled)
    }

    fun saveToken(token: String?) {
        notesSettings.setToken(token)
    }
}
