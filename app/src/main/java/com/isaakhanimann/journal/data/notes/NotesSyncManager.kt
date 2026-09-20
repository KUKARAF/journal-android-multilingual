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
import com.isaakhanimann.journal.data.room.experiences.JournalDataEvents
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.settings.ExperienceSerializable
import com.isaakhanimann.journal.ui.tabs.settings.LocationSerializable
import com.isaakhanimann.journal.ui.tabs.settings.RatingSerializable
import com.isaakhanimann.journal.ui.tabs.settings.TimedNoteSerializable
import com.isaakhanimann.journal.ui.tabs.settings.toIngestionSerializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
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
        // MERGE: read the current note so SoloForge's aggregate keys and the shared substance stat
        // lines survive; never delete, or an emptied journal day would wipe another app's data.
        val existing = notesClient.getNote(path).getOrNull()?.content
        if (experiences.isEmpty() && existing == null) return
        val markdown = DailyNoteMapper.render(localDate, experiences, existing)
        notesClient.putNote(path = path, content = markdown)
    }

    // ---- Shared /api/stats channel: publish native ingestions (write side) ----

    /**
     * Publish a just-logged ingestion to the shared `/api/stats` channel when its substance is on
     * the allowlist (alcohol/caffeine/nicotine). Fire-and-forget; requires a token. Re-imports of
     * this value are prevented by the minute-level de-dup in [importSubstancesFromStats].
     */
    fun onIngestionLoggedForStats(ingestion: Ingestion) {
        scope.launch {
            runCatching {
                if (settings.bearerToken().isNullOrBlank()) return@runCatching
                val stat = SubstanceStatsCatalog.forSubstanceName(ingestion.substanceName)
                    ?: return@runCatching
                val dose = ingestion.dose ?: return@runCatching
                val zoned = ingestion.time.atZone(zoneId())
                val date = zoned.toLocalDate().toString()
                val at = "%02d%02d".format(Locale.ROOT, zoned.hour, zoned.minute)
                notesClient.putStatRegistry(stat.key, stat.unit, stat.label, stat.chart, stat.agg)
                notesClient.postStat(stat.key, dose.roundToInt(), at, date)
            }.onFailure { logFailure(it) }
        }
    }

    // ---- Shared /api/stats channel: import substances as ingestions (read side) ----

    /** Startup pull: import allowlisted substances from the shared channel as real ingestions. */
    fun runStartupImport() {
        scope.launch {
            runCatching { importSubstancesFromStats() }.onFailure { logFailure(it) }
        }
    }

    suspend fun importSubstancesFromStats() {
        if (settings.bearerToken().isNullOrBlank()) return
        val zone = zoneId()
        val today = LocalDate.now(zone)
        val from = today.minusDays(IMPORT_WINDOW_DAYS)

        // Register the shared metrics (idempotent) so GET returns them for both apps.
        SubstanceStatsCatalog.all.forEach { stat ->
            notesClient.putStatRegistry(stat.key, stat.unit, stat.label, stat.chart, stat.agg)
        }

        val series = notesClient
            .getStats(SubstanceStatsCatalog.metricKeys, from.toString(), today.toString())
            .getOrNull() ?: return

        val windowStart = from.atStartOfDay(zone).toInstant()
        val windowEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val existing = experienceDao
            .getExperiencesWithIngestionsTimedNotesAndRatingsInRange(windowStart, windowEnd)

        // De-dup key = (lowercased substance name, epoch minute). Seeded from every existing
        // ingestion, so re-runs AND this app's own /api/stats writes are never re-imported.
        val seenKeys = HashSet<String>()
        val dayToExperienceId = HashMap<LocalDate, Int>()
        existing.forEach { day ->
            val localDate = day.experience.sortDate.atZone(zone).toLocalDate()
            dayToExperienceId.putIfAbsent(localDate, day.experience.id)
            day.ingestions.forEach { ingestion ->
                seenKeys.add(dedupKey(ingestion.substanceName, ingestion.time))
            }
        }
        val companionNames = experienceDao.getAllSubstanceCompanions()
            .mapTo(HashSet()) { it.substanceName.lowercase() }

        var inserted = 0
        series.forEach { serie ->
            val stat = SubstanceStatsCatalog.forKey(serie.metric) ?: return@forEach
            val unit = serie.unit ?: stat.unit
            serie.days.forEach { day ->
                val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return@forEach
                day.points.forEach point@{ point ->
                    val at = point.at ?: return@point
                    val time = parseAtTime(date, at, zone) ?: return@point
                    val key = dedupKey(stat.substanceName, time)
                    if (!seenKeys.add(key)) return@point
                    val experienceId = dayToExperienceId.getOrPut(date) {
                        createDayExperience(date, zone)
                    }
                    experienceDao.insert(
                        Ingestion(
                            substanceName = stat.substanceName,
                            time = time,
                            endTime = null,
                            creationDate = Instant.now(),
                            administrationRoute = AdministrationRoute.ORAL,
                            dose = point.value,
                            isDoseAnEstimate = false,
                            estimatedDoseStandardDeviation = null,
                            units = unit,
                            experienceId = experienceId,
                            notes = IMPORT_MARKER,
                            stomachFullness = null,
                            consumerName = null,
                            customUnitId = null,
                            releaseForm = null
                        )
                    )
                    if (companionNames.add(stat.substanceName.lowercase())) {
                        experienceDao.insert(SubstanceCompanion(stat.substanceName, colorFor(stat.key)))
                    }
                    inserted++
                }
            }
        }
        if (inserted > 0) JournalDataEvents.notifyJournalChanged()
    }

    private suspend fun createDayExperience(date: LocalDate, zone: ZoneId): Int {
        val sortDate = date.atTime(12, 0).atZone(zone).toInstant()
        return experienceDao.insert(
            Experience(
                id = 0,
                title = date.toString(),
                text = "",
                creationDate = Instant.now(),
                sortDate = sortDate,
                isFavorite = false,
                location = null
            )
        ).toInt()
    }

    private fun dedupKey(substanceName: String, time: Instant): String =
        substanceName.lowercase() + "|" + (time.epochSecond / 60)

    // Accepts both the GET form "HH:MM" and the POST form "HHMM"; ignores anything else.
    private fun parseAtTime(date: LocalDate, at: String, zone: ZoneId): Instant? {
        val digits = at.filter { it.isDigit() }
        val hour: Int
        val minute: Int
        when (digits.length) {
            4 -> {
                hour = digits.substring(0, 2).toInt()
                minute = digits.substring(2, 4).toInt()
            }
            3 -> {
                hour = digits.substring(0, 1).toInt()
                minute = digits.substring(1, 3).toInt()
            }
            else -> return null
        }
        if (hour > 23 || minute > 59) return null
        return date.atTime(hour, minute).atZone(zone).toInstant()
    }

    private fun colorFor(key: String): AdaptiveColor = when (key) {
        "alcohol" -> AdaptiveColor.RED
        "caffeine" -> AdaptiveColor.ORANGE
        else -> AdaptiveColor.GREEN
    }

    private suspend fun isEnabledWithToken(): Boolean =
        settings.isSyncEnabled() && !settings.bearerToken().isNullOrBlank()

    private fun zoneId(): ZoneId = ZoneId.systemDefault()

    private fun logFailure(throwable: Throwable) {
        Log.w("NotesSyncManager", "Notes sync failed", throwable)
    }

    private companion object {
        const val IMPORT_WINDOW_DAYS = 30L
        const val IMPORT_MARKER = "Imported from notes server"
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
