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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Metadata block returned by the rust_note server for every note.
 * All fields are optional so a server that adds or omits fields does not break decoding.
 */
@Serializable
data class NoteMeta(
    val id: String? = null,
    val title: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val version: String? = null
)

/** Response of `GET /api/notes/{path}`. */
@Serializable
data class GetNoteResponse(
    val meta: NoteMeta,
    val content: String
)

/** Body of `POST /api/notes`. */
@Serializable
data class CreateNoteRequest(
    @SerialName("id_or_title") val idOrTitle: String,
    val content: String
)

/** Body of `PUT /api/notes/{path}`. */
@Serializable
data class PutNoteRequest(
    val content: String,
    @SerialName("expected_version") val expectedVersion: String? = null
)
