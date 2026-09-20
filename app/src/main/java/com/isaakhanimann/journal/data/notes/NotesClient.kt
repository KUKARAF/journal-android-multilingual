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

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when the notes server answers with a non-success status code. */
class NotesHttpException(val status: Int, message: String) : RuntimeException(message)

/**
 * Thin client for the rust_note server at notes.osmosis.page. All routes live under `/api/notes`
 * and are authenticated with a device bearer token. Every call is [Result]-wrapped so callers can
 * decide how to react to offline / auth / conflict situations without a try/catch.
 */
@Singleton
class NotesClient @Inject constructor(
    private val httpClient: HttpClient,
    private val config: NotesConfig
) {
    private suspend fun baseUrl(): String = config.baseUrl().trimEnd('/')

    private suspend fun notesUrl(path: String): String = "${baseUrl()}/api/notes/$path"

    private fun HttpResponse.orThrow(): HttpResponse {
        if (!status.isSuccess()) {
            throw NotesHttpException(status.value, "Notes server returned ${status.value}")
        }
        return this
    }

    // The request-builder lambda is not suspend, so the token is resolved beforehand and applied here.
    private fun HttpRequestBuilder.applyAuth(token: String?) {
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    /** GET a note by path. Returns `success(null)` when the note does not exist (404). */
    suspend fun getNote(path: String): Result<GetNoteResponse?> = runCatching {
        val token = config.bearerToken()
        val url = notesUrl(path)
        val response = httpClient.get(url) { applyAuth(token) }
        if (response.status == HttpStatusCode.NotFound) {
            return@runCatching null
        }
        response.orThrow().body<GetNoteResponse>()
    }

    /** POST a new note. Fails with [NotesHttpException] status 409 when it already exists. */
    suspend fun createNote(idOrTitle: String, content: String): Result<NoteMeta> = runCatching {
        val token = config.bearerToken()
        val url = "${baseUrl()}/api/notes"
        val response = httpClient.post(url) {
            applyAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateNoteRequest(idOrTitle = idOrTitle, content = content))
        }
        response.orThrow().body<NoteMeta>()
    }

    /** PUT the full markdown of a note (auto-creates if missing). Fails 409 on a stale version. */
    suspend fun putNote(
        path: String,
        content: String,
        expectedVersion: String? = null
    ): Result<NoteMeta> = runCatching {
        val token = config.bearerToken()
        val url = notesUrl(path)
        val response = httpClient.put(url) {
            applyAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PutNoteRequest(content = content, expectedVersion = expectedVersion))
        }
        response.orThrow().body<NoteMeta>()
    }

    /** DELETE a note by path. A missing note (404) is treated as success. */
    suspend fun deleteNote(path: String): Result<Unit> = runCatching {
        val token = config.bearerToken()
        val url = notesUrl(path)
        val response = httpClient.delete(url) { applyAuth(token) }
        if (response.status == HttpStatusCode.NotFound) {
            return@runCatching
        }
        response.orThrow()
    }
}
