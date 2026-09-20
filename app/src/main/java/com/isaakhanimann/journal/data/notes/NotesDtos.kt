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

// ---- /api/stats shared-metrics channel ----

/** One raw timed sample. `at` is `HH:MM` on GET (and may be null for an untimed sample). */
@Serializable
data class StatPoint(
    val value: Double = 0.0,
    val at: String? = null
)

/** Per-day aggregate plus its raw points. */
@Serializable
data class StatDay(
    val date: String,
    val value: Double = 0.0,
    val points: List<StatPoint> = emptyList()
)

/** One registered metric's series over the queried window. */
@Serializable
data class StatSeries(
    val metric: String,
    val label: String? = null,
    val unit: String? = null,
    val chart: String? = null,
    val agg: String? = null,
    val days: List<StatDay> = emptyList()
)

/** Response of `GET /api/stats`. */
@Serializable
data class StatsResponse(
    val series: List<StatSeries> = emptyList()
)

/** Body of `POST /api/stats`. `at` is `HHMM` (24h) or omitted for an untimed sample. */
@Serializable
data class PostStatRequest(
    val key: String,
    val value: Int,
    val at: String? = null,
    val date: String
)

/** Body of `PUT /api/stats/registry/{metric}`. */
@Serializable
data class StatRegistryRequest(
    val unit: String,
    val label: String,
    val chart: String,
    val agg: String
)
