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

/**
 * Runtime configuration the [NotesClient] needs for every request. Kept as an interface so the
 * client can be unit tested with a fake, without pulling in DataStore or the Android keystore.
 */
interface NotesConfig {
    /** Base URL of the notes server, e.g. `https://notes.osmosis.page` (no trailing slash). */
    suspend fun baseUrl(): String

    /** Device bearer token, or null when the user has not stored one yet. */
    suspend fun bearerToken(): String?
}

const val DEFAULT_NOTES_BASE_URL = "https://notes.osmosis.page"
