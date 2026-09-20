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

package com.isaakhanimann.journal.data.room.experiences

import com.isaakhanimann.journal.data.notes.NotesSyncManager
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRating
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.room.experiences.entities.TimedNote
import com.isaakhanimann.journal.data.room.experiences.relations.CustomUnitWithIngestions
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestions
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWindowCounts
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanion
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithExperienceAndCustomUnit
import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/**
 * Room-backed journal repository. Room stays the source of truth / offline cache; when notes-server
 * sync is enabled and a token is stored, experience-affecting writes additionally push the affected
 * day's note to the server via [notesSyncManager] (fire-and-forget, so behavior is unchanged when
 * the feature is off). Implements [ExperienceRepositoryInterface] while remaining injectable by its
 * concrete type, so the ~38 existing consumers keep compiling unchanged.
 */
@Singleton
class ExperienceRepository @Inject constructor(
    private val experienceDao: ExperienceDao,
    private val notesSyncManager: NotesSyncManager
) : ExperienceRepositoryInterface {
    override suspend fun insert(rating: ShulginRating) = experienceDao.insert(rating).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceChanged(rating.experienceId) }
    override suspend fun insert(customUnit: CustomUnit) = experienceDao.insert(customUnit).also { JournalDataEvents.notifyJournalChanged() }.toInt()
    override suspend fun insert(timedNote: TimedNote) = experienceDao.insert(timedNote).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceChanged(timedNote.experienceId) }
    override suspend fun update(experience: Experience) = experienceDao.update(experience).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceDayChanged(experience.sortDate) }
    override suspend fun update(ingestion: Ingestion) = experienceDao.update(ingestion).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceChanged(ingestion.experienceId) }
    override suspend fun update(rating: ShulginRating) = experienceDao.update(rating).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceChanged(rating.experienceId) }
    override suspend fun update(customUnit: CustomUnit) = experienceDao.update(customUnit).also { JournalDataEvents.notifyJournalChanged() }
    override suspend fun update(timedNote: TimedNote) = experienceDao.update(timedNote).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceChanged(timedNote.experienceId) }

    override suspend fun migrateBenzydamine() = experienceDao.migrateBenzydamine()
    override suspend fun migrateCannabisAndMushroomUnits() = experienceDao.migrateCannabisAndMushroomUnits()
    override suspend fun insertIngestionExperienceAndCompanion(
        ingestion: Ingestion,
        experience: Experience,
        substanceCompanion: SubstanceCompanion
    ) = experienceDao.insertIngestionExperienceAndCompanion(
        ingestion,
        experience,
        substanceCompanion
    ).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceDayChanged(experience.sortDate) }

    override suspend fun insertEverything(journalExport: JournalExport) =
        experienceDao.insertEverything(journalExport)
            .also { JournalDataEvents.notifyJournalChanged() }
            .also { notesSyncManager.onBulkChanged() }

    override suspend fun replaceEverything(journalExport: JournalExport) =
        experienceDao.replaceEverything(journalExport)
            .also { JournalDataEvents.notifyJournalChanged() }
            .also { notesSyncManager.onBulkChanged() }

    override suspend fun insertIngestionAndCompanion(
        ingestion: Ingestion,
        substanceCompanion: SubstanceCompanion
    ) = experienceDao.insertIngestionAndCompanion(
        ingestion,
        substanceCompanion
    ).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceChanged(ingestion.experienceId) }

    override suspend fun deleteEverything() = experienceDao.deleteEverything()
        .also { JournalDataEvents.notifyJournalChanged() }
    // Note: deleteEverything intentionally does not mass-delete remote notes (owner-scoped, risky).

    override suspend fun delete(ingestion: Ingestion) = experienceDao.delete(ingestion).also { JournalDataEvents.notifyJournalChanged() }.also { notesSyncManager.onExperienceChanged(ingestion.experienceId) }
    override suspend fun delete(customUnit: CustomUnit) = experienceDao.delete(customUnit).also { JournalDataEvents.notifyJournalChanged() }

    override suspend fun deleteEverythingOfExperience(experienceId: Int) {
        val sortDate = experienceDao.getExperience(experienceId)?.sortDate
        experienceDao.deleteEverythingOfExperience(experienceId)
        JournalDataEvents.notifyJournalChanged()
        if (sortDate != null) notesSyncManager.onExperienceDayChanged(sortDate)
    }

    override suspend fun delete(experience: Experience) = experienceDao.delete(experience)
        .also { JournalDataEvents.notifyJournalChanged() }
        .also { notesSyncManager.onExperienceDayChanged(experience.sortDate) }

    override suspend fun delete(rating: ShulginRating) = experienceDao.delete(rating)
        .also { JournalDataEvents.notifyJournalChanged() }
        .also { notesSyncManager.onExperienceChanged(rating.experienceId) }

    override suspend fun delete(timedNote: TimedNote) = experienceDao.delete(timedNote)
        .also { JournalDataEvents.notifyJournalChanged() }
        .also { notesSyncManager.onExperienceChanged(timedNote.experienceId) }

    override suspend fun delete(experienceWithIngestions: ExperienceWithIngestions) =
        experienceDao.deleteExperienceWithIngestions(experienceWithIngestions)
            .also { JournalDataEvents.notifyJournalChanged() }
            .also { notesSyncManager.onExperienceDayChanged(experienceWithIngestions.experience.sortDate) }

    override suspend fun deleteUnusedSubstanceCompanions() =
        experienceDao.deleteUnusedSubstanceCompanions()

    override suspend fun getSortedExperiencesWithIngestionsWithSortDateBetween(
        fromInstant: Instant,
        toInstant: Instant
    ): List<ExperienceWithIngestions> =
        experienceDao.getSortedExperiencesWithIngestionsWithSortDateBetween(fromInstant, toInstant)

    override fun getSortedExperienceWithIngestionsCompanionsAndRatingsFlow(): Flow<List<ExperienceWithIngestionsCompanionsAndRatings>> =
        experienceDao.getSortedExperienceWithIngestionsCompanionsAndRatingsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getSortedExperiencesWithIngestionsFlow(): Flow<List<ExperienceWithIngestions>> =
        experienceDao.getSortedExperiencesWithIngestionsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getSortedExperiencesWithIngestionsAndCustomUnitsFlow(): Flow<List<ExperienceWithIngestionsAndCompanions>> =
        experienceDao.getSortedExperiencesWithIngestionsAndCustomUnitsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getCustomSubstancesFlow(): Flow<List<CustomSubstance>> =
        experienceDao.getCustomSubstancesFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getCustomSubstanceFlow(id: Int): Flow<CustomSubstance?> =
        experienceDao.getCustomSubstanceFlow(id)
            .flowOn(Dispatchers.IO)
            .conflate()

    override suspend fun getCustomSubstance(name: String): CustomSubstance? =
        experienceDao.getCustomSubstance(name)

    override fun getIngestionsWithExperiencesFlow(
        fromInstant: Instant,
        toInstant: Instant
    ): Flow<List<IngestionWithExperienceAndCustomUnit>> =
        experienceDao.getIngestionWithExperiencesFlow(fromInstant, toInstant)
            .flowOn(Dispatchers.IO)
            .conflate()

    override suspend fun getIngestionsWithCompanions(
        fromInstant: Instant,
        toInstant: Instant
    ): List<IngestionWithCompanion> =
        experienceDao.getIngestionsWithCompanions(fromInstant, toInstant)

    override suspend fun getIngestionWindowCounts(
        fromInstant: Instant,
        toInstant: Instant,
        substanceName: String?
    ): IngestionWindowCounts =
        experienceDao.getIngestionWindowCounts(fromInstant, toInstant, substanceName)

    override fun getSortedLastUsedSubstanceNamesFlow(limit: Int): Flow<List<String>> =
        experienceDao.getSortedLastUsedSubstanceNamesFlow(limit).flowOn(Dispatchers.IO).conflate()

    override suspend fun getExperience(id: Int): Experience? = experienceDao.getExperience(id)
    override suspend fun getExperienceWithIngestionsCompanionsAndRatings(id: Int): ExperienceWithIngestionsCompanionsAndRatings? =
        experienceDao.getExperienceWithIngestionsCompanionsAndRatings(id)

    override suspend fun getIngestionsWithCompanions(experienceId: Int) =
        experienceDao.getIngestionsWithCompanions(experienceId)

    override suspend fun getRating(id: Int): ShulginRating? = experienceDao.getRating(id)
    override suspend fun getTimedNote(id: Int): TimedNote? = experienceDao.getTimedNote(id)
    override suspend fun getCustomUnit(id: Int): CustomUnit? = experienceDao.getCustomUnit(id)
    override suspend fun getCustomUnitWithIngestions(id: Int): CustomUnitWithIngestions? = experienceDao.getCustomUnitWithIngestions(id)
    override fun getIngestionFlow(id: Int) = experienceDao.getIngestionFlow(id)
        .flowOn(Dispatchers.IO)
        .conflate()

    override fun getIngestionsWithCompanionsFlow(experienceId: Int) =
        experienceDao.getIngestionsWithCompanionsFlow(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getRatingsFlow(experienceId: Int) = experienceDao.getRatingsFlow(experienceId)
        .flowOn(Dispatchers.IO)
        .conflate()

    override fun getTimedNotesFlowSorted(experienceId: Int) =
        experienceDao.getTimedNotesFlowSorted(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getExperienceFlow(experienceId: Int) = experienceDao.getExperienceFlow(experienceId)
        .flowOn(Dispatchers.IO)
        .conflate()

    override suspend fun getLatestIngestionOfEverySubstanceSinceDate(instant: Instant): List<Ingestion> =
        experienceDao.getLatestIngestionOfEverySubstanceSinceDate(instant)

    override suspend fun getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted(): List<ExperienceWithIngestionsTimedNotesAndRatings> =
        experienceDao.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted()

    override suspend fun getExperiencesWithIngestionsTimedNotesAndRatingsInRange(
        fromInstant: Instant,
        toInstant: Instant
    ): List<ExperienceWithIngestionsTimedNotesAndRatings> =
        experienceDao.getExperiencesWithIngestionsTimedNotesAndRatingsInRange(
            fromInstant,
            toInstant
        )

    override suspend fun getAllCustomUnitsSorted(): List<CustomUnit> =
        experienceDao.getAllCustomUnitsSorted()

    override suspend fun getAllCustomSubstances(): List<CustomSubstance> =
        experienceDao.getAllCustomSubstances()

    override suspend fun getAllSubstanceCompanions(): List<SubstanceCompanion> =
        experienceDao.getAllSubstanceCompanions()

    override suspend fun getTimedNotes(experienceId: Int): List<TimedNote> =
        experienceDao.getTimedNotes(experienceId)

    override suspend fun delete(substanceCompanion: SubstanceCompanion) =
        experienceDao.delete(substanceCompanion)

    override suspend fun update(substanceCompanion: SubstanceCompanion) =
        experienceDao.update(substanceCompanion)

    override suspend fun insert(customSubstance: CustomSubstance): Int =
        experienceDao.insert(customSubstance).toInt()

    override suspend fun delete(customSubstance: CustomSubstance) = experienceDao.delete(customSubstance)

    override suspend fun update(customSubstance: CustomSubstance) = experienceDao.update(customSubstance)

    override fun getSortedIngestionsWithSubstanceCompanionsFlow(limit: Int) =
        experienceDao.getSortedIngestionsWithSubstanceCompanionsFlow(limit)
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getSortedIngestions(limit: Int) = experienceDao.getSortedIngestions(limit)
        .flowOn(Dispatchers.IO)
        .conflate()

    override fun getSortedIngestionsFlow() = experienceDao.getSortedIngestionsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    override fun getSortedIngestionsFlow(substanceName: String, limit: Int) =
        experienceDao.getSortedIngestionsFlow(substanceName, limit)
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName: String) =
        experienceDao.getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName)
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getAllSubstanceCompanionsFlow() = experienceDao.getAllSubstanceCompanionsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    override fun getCustomUnitsFlow(isArchived: Boolean) = experienceDao.getSortedCustomUnitsFlow(isArchived)
        .flowOn(Dispatchers.IO)
        .conflate()

    override fun getUnArchivedCustomUnitsFlow(substanceName: String) =
        experienceDao.getSortedCustomUnitsFlowBasedOnName(substanceName, false)
            .flowOn(Dispatchers.IO)
            .conflate()

    override fun getAllCustomUnitsFlow() = experienceDao.getAllCustomUnitsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    override fun getSubstanceCompanionFlow(substanceName: String) =
        experienceDao.getSubstanceCompanionFlow(substanceName)
            .flowOn(Dispatchers.IO)
            .conflate()
}
