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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesClientTest {

    private val config = object : NotesConfig {
        override suspend fun baseUrl(): String = "https://notes.example"
        override suspend fun bearerToken(): String? = "tok-123"
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun notesClient(engine: MockEngine): NotesClient {
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return NotesClient(http, config)
    }

    @Test
    fun getNoteReturnsNullOn404() = runTest {
        val client = notesClient(MockEngine { respond("not found", HttpStatusCode.NotFound) })
        val result = client.getNote("diary/2026-09-20")
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun getNoteParsesBodyAndSendsBearer() = runTest {
        var seenAuth: String? = null
        var seenUrl: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            seenUrl = request.url.toString()
            respond(
                content = """{"meta":{"id":"diary/2026-09-20","version":"abc"},"content":"---\ndate: \"2026-09-20\"\n---\n"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val note = notesClient(engine).getNote("diary/2026-09-20").getOrThrow()
        assertEquals("diary/2026-09-20", note?.meta?.id)
        assertEquals("abc", note?.meta?.version)
        assertEquals("Bearer tok-123", seenAuth)
        assertTrue(seenUrl!!.endsWith("/api/notes/diary/2026-09-20"))
    }

    @Test
    fun putNoteSendsContentAndReturnsMeta() = runTest {
        var seenMethod: HttpMethod? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenBody = (request.body as TextContent).text
            respond(
                content = """{"id":"diary/2026-09-20","version":"v2"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val meta = notesClient(engine).putNote("diary/2026-09-20", "---\nbody\n---\n").getOrThrow()
        assertEquals("v2", meta.version)
        assertEquals(HttpMethod.Put, seenMethod)
        assertTrue(seenBody!!.contains("---"))
    }

    @Test
    fun createNoteConflictFailsWith409() = runTest {
        val client = notesClient(MockEngine { respond("exists", HttpStatusCode.Conflict) })
        val result = client.createNote("diary/2026-09-20", "---\n---\n")
        assertFalse(result.isSuccess)
        val error = result.exceptionOrNull()
        assertTrue(error is NotesHttpException)
        assertEquals(409, (error as NotesHttpException).status)
    }

    @Test
    fun deleteNoteSucceedsOn404() = runTest {
        val client = notesClient(MockEngine { respond("gone", HttpStatusCode.NotFound) })
        val result = client.deleteNote("diary/2026-09-20")
        assertTrue(result.isSuccess)
    }
}
