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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.content.Intent
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.CardWithTitle

/**
 * Settings card for the notes.osmosis.page daily-note sync: an enable flag, the server base URL and
 * the device bearer token (stored encrypted). Self-contained so it can be dropped into the existing
 * Settings screen without changing its signature.
 */
@Composable
fun NotesSyncCard(
    viewModel: NotesSyncViewModel = hiltViewModel()
) {
    val isSyncEnabled by viewModel.isSyncEnabledFlow.collectAsState()
    val baseUrl by viewModel.baseUrlFlow.collectAsState()
    val hasToken by viewModel.hasTokenFlow.collectAsState()

    var baseUrlDraft by remember(baseUrl) { mutableStateOf(baseUrl) }
    var tokenDraft by remember { mutableStateOf("") }
    val context = LocalContext.current

    CardWithTitle(title = "Notes server sync", innerPaddingHorizontal = 15.dp) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync journal to notes server",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isSyncEnabled,
                    onCheckedChange = { viewModel.saveSyncEnabled(it) }
                )
            }
            Text(
                text = "Stores each day's experiences as a daily note (diary/YYYY-MM-DD) with YAML " +
                    "frontmatter. Room stays the source of truth; the server is a mirror.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (hasToken) "Status: connected (token stored)" else "Status: not connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val loginUrl = baseUrl.trimEnd('/') + "/auth/login?client=app"
                    context.startActivity(Intent(Intent.ACTION_VIEW, loginUrl.toUri()))
                }
            ) {
                Text(if (hasToken) "Sign in again" else "Sign in to notes server")
            }
            Text(
                text = "Opens the browser to sign in; the app captures the token automatically " +
                    "via the dev.rustnote.app://auth callback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = baseUrlDraft,
                onValueChange = { baseUrlDraft = it },
                label = { Text("Server base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    enabled = baseUrlDraft.isNotBlank() && baseUrlDraft != baseUrl,
                    onClick = { viewModel.saveBaseUrl(baseUrlDraft) }
                ) {
                    Text("Save URL")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Manual token (fallback)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = { Text("Paste bearer token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasToken) {
                    Button(onClick = { viewModel.saveToken(null) }) {
                        Text("Clear")
                    }
                }
                Button(
                    enabled = tokenDraft.isNotBlank(),
                    onClick = {
                        viewModel.saveToken(tokenDraft)
                        tokenDraft = ""
                    }
                ) {
                    Text("Save token")
                }
            }
        }
    }
}
