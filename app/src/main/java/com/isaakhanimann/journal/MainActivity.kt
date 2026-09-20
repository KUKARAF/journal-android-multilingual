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

package com.isaakhanimann.journal

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.isaakhanimann.journal.data.notes.NotesSettingsRepository
import com.isaakhanimann.journal.ui.main.MainScreen
import com.isaakhanimann.journal.ui.theme.JournalTheme
import com.isaakhanimann.journal.ui.widgets.StatsWidgetSync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var notesSettingsRepository: NotesSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthDeepLink(intent)
        // Screen-on refreshes for the effect-notification timeline register once
        // here; actual re-renders run through the activity's view tree below.
        val app = application as com.isaakhanimann.journal.di.JournalApplication
        com.isaakhanimann.journal.ui.notifications.EffectNotificationRefresher.register(
            this,
            app.applicationScope
        )
        setContent {
            JournalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    /**
     * Captures the native OIDC callback dev.rustnote.app://auth?token=<raw> and persists the raw
     * bearer token to encrypted storage, so [NotesClient] can authenticate afterwards.
     */
    private fun handleAuthDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "dev.rustnote.app" && data.host == "auth") {
            val token = data.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                notesSettingsRepository.setToken(token)
                Toast.makeText(this, "Connected to notes server", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Foregrounded again: the activity view tree is available, so any
        // pending (dirty or overdue) timeline refresh can render now.
        val root = findViewById<android.view.View>(android.R.id.content)
        com.isaakhanimann.journal.ui.notifications.EffectNotificationRefresher.flushIfDue(
            this,
            root,
            force = false
        )
        // Keep the stats widget in sync: it may have been placed, reconfigured or left
        // stale while the app was away. This only enqueues a conflated, off-main refresh
        // and is a no-op when no widget is placed.
        StatsWidgetSync.requestRefresh()
    }
}
