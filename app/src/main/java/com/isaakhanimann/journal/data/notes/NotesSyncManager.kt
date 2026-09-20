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

import android.util.Log
import com.isaakhanimann.journal.data.room.experiences.ExperienceDao
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import com.isaakhanimann.journal.ui.tabs.settings.ExperienceSerializable
import com.isaakhanimann.journal.ui.tabs.settings.LocationSerializable
import com.isaakhanimann.journal.ui.tabs.settings.RatingSerializable
import com.isaakhanimann.journal.ui.tabs.settings.TimedNoteSerializable
import com.isaakhanimann.journal.ui.tabs.settings.toIngestionSerializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Mirrors experience-day writes to the notes server as daily notes. Fire-and-forget and entirely
 * opt-in: every trigger first checks the sync flag and a stored token, then rebuilds the affected
 * day from Room (the source of truth / offline cache) and pushes it. All failures are swallowed so
 * being offline or unauthenticated never breaks the local write path.
 */
@Singleton
class NotesSyncManager @Inject constructor(
    private val experienceDao: ExperienceDao,
    private val notesClient: NotesClient,
    private val settings: NotesSettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Push the day that the given [sortDate] falls on (local time). */
    fun onExperienceDayChanged(sortDate: Instant) {
        scope.launch {
            runCatching { pushDay(sortDate.atZone(zoneId()).toLocalDate()) }
                .onFailure { logFailure(it) }
        }
    }

    /** Resolve the experience's day from Room, then push it. Use when only the id is known. */
    fun onExperienceChanged(experienceId: Int) {
        scope.launch {
            runCatching {
                val experience = experienceDao.getExperience(experienceId) ?: return@runCatching
                pushDay(experience.sortDate.atZone(zoneId()).toLocalDate())
            }.onFailure { logFailure(it) }
        }
    }

    /** Push every day that currently holds at least one experience (e.g. after an import). */
    fun onBulkChanged() {
        scope.launch {
            runCatching {
                if (!isEnabledWithToken()) return@runCatching
                val days = experienceDao
                    .getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted()
                    .map { it.experience.sortDate.atZone(zoneId()).toLocalDate() }
                    .toSet()
                days.forEach { pushDay(it) }
            }.onFailure { logFailure(it) }
        }
    }

    private suspend fun pushDay(localDate: LocalDate) {
        if (!isEnabledWithToken()) return
        val zone = zoneId()
        val startOfDay = localDate.atStartOfDay(zone).toInstant()
        val endOfDay = localDate.plusDays(1).atStartOfDay(zone).toInstant()
        val experiences = experienceDao
            .getExperiencesWithIngestionsTimedNotesAndRatingsInRange(startOfDay, endOfDay)
            .map { it.toExperienceSerializable() }
        val path = DailyNoteMapper.dayId(localDate)
        if (experiences.isEmpty()) {
            notesClient.deleteNote(path)
        } else {
            val markdown = DailyNoteMapper.toMarkdown(localDate, experiences)
            notesClient.putNote(path = path, content = markdown)
        }
    }

    private suspend fun isEnabledWithToken(): Boolean =
        settings.isSyncEnabled() && !settings.bearerToken().isNullOrBlank()

    private fun zoneId(): ZoneId = ZoneId.systemDefault()

    private fun logFailure(throwable: Throwable) {
        Log.w("NotesSyncManager", "Daily-note sync failed", throwable)
    }
}

/** Same flattening the JSON export uses, so a synced daily note matches the backup shape. */
internal fun ExperienceWithIngestionsTimedNotesAndRatings.toExperienceSerializable(): ExperienceSerializable {
    val location = experience.location
    return ExperienceSerializable(
        title = experience.title,
        text = experience.text,
        creationDate = experience.creationDate,
        sortDate = experience.sortDate,
        isFavorite = experience.isFavorite,
        ingestions = ingestions.map { it.toIngestionSerializable() },
        location = if (location != null) {
            LocationSerializable(
                name = location.name,
                latitude = location.latitude,
                longitude = location.longitude
            )
        } else {
            null
        },
        ratings = ratings.map { rating ->
            RatingSerializable(
                option = rating.option,
                time = rating.time,
                creationDate = rating.creationDate
            )
        },
        timedNotes = timedNotes.map { timedNote ->
            TimedNoteSerializable(
                creationDate = timedNote.creationDate,
                time = timedNote.time,
                note = timedNote.note,
                color = timedNote.color,
                isPartOfTimeline = timedNote.isPartOfTimeline
            )
        }
    )
}
