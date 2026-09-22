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
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithExperienceAndCustomUnit
import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Method-for-method contract of [ExperienceRepository], extracted so the journal storage seam can
 * be depended on by interface (and decorated for remote sync). Room remains the source of truth;
 * the concrete implementation additionally mirrors experience-day writes to the notes server.
 */
interface ExperienceRepositoryInterface {
    suspend fun insert(rating: ShulginRating)
    suspend fun insert(customUnit: CustomUnit): Int
    suspend fun insert(timedNote: TimedNote)
    suspend fun update(experience: Experience)
    suspend fun update(ingestion: Ingestion)
    suspend fun update(rating: ShulginRating)
    suspend fun update(customUnit: CustomUnit)
    suspend fun update(timedNote: TimedNote)

    suspend fun migrateBenzydamine()
    suspend fun migrateCannabisAndMushroomUnits()
    suspend fun insertIngestionExperienceAndCompanion(
        ingestion: Ingestion,
        experience: Experience,
        substanceCompanion: SubstanceCompanion
    )

    suspend fun insertEverything(journalExport: JournalExport)
    suspend fun replaceEverything(journalExport: JournalExport)

    suspend fun insertIngestionAndCompanion(
        ingestion: Ingestion,
        substanceCompanion: SubstanceCompanion
    )

    suspend fun deleteEverything()

    suspend fun delete(ingestion: Ingestion)
    suspend fun delete(customUnit: CustomUnit)

    suspend fun deleteEverythingOfExperience(experienceId: Int)

    suspend fun delete(experience: Experience)
    suspend fun delete(rating: ShulginRating)
    suspend fun delete(timedNote: TimedNote)
    suspend fun delete(experienceWithIngestions: ExperienceWithIngestions)

    suspend fun deleteUnusedSubstanceCompanions()

    suspend fun getSortedExperiencesWithIngestionsWithSortDateBetween(
        fromInstant: Instant,
        toInstant: Instant
    ): List<ExperienceWithIngestions>

    fun getSortedExperienceWithIngestionsCompanionsAndRatingsFlow(): Flow<List<ExperienceWithIngestionsCompanionsAndRatings>>

    fun getSortedExperiencesWithIngestionsFlow(): Flow<List<ExperienceWithIngestions>>

    fun getSortedExperiencesWithIngestionsAndCustomUnitsFlow(): Flow<List<ExperienceWithIngestionsAndCompanions>>

    fun getCustomSubstancesFlow(): Flow<List<CustomSubstance>>

    fun getCustomSubstanceFlow(id: Int): Flow<CustomSubstance?>

    suspend fun getCustomSubstance(name: String): CustomSubstance?

    fun getIngestionsWithExperiencesFlow(
        fromInstant: Instant,
        toInstant: Instant
    ): Flow<List<IngestionWithExperienceAndCustomUnit>>

    suspend fun getIngestionsWithCompanions(
        fromInstant: Instant,
        toInstant: Instant
    ): List<IngestionWithCompanion>

    suspend fun getIngestionWindowCounts(
        fromInstant: Instant,
        toInstant: Instant,
        substanceName: String?
    ): IngestionWindowCounts

    fun getSortedLastUsedSubstanceNamesFlow(limit: Int): Flow<List<String>>

    suspend fun getExperience(id: Int): Experience?
    suspend fun getExperienceWithIngestionsCompanionsAndRatings(id: Int): ExperienceWithIngestionsCompanionsAndRatings?

    suspend fun getIngestionsWithCompanions(experienceId: Int): List<IngestionWithCompanionAndCustomUnit>

    suspend fun getRating(id: Int): ShulginRating?
    suspend fun getTimedNote(id: Int): TimedNote?
    suspend fun getCustomUnit(id: Int): CustomUnit?
    suspend fun getCustomUnitWithIngestions(id: Int): CustomUnitWithIngestions?
    fun getIngestionFlow(id: Int): Flow<IngestionWithCompanionAndCustomUnit?>

    fun getIngestionsWithCompanionsFlow(experienceId: Int): Flow<List<IngestionWithCompanionAndCustomUnit>>

    fun getRatingsFlow(experienceId: Int): Flow<List<ShulginRating>>

    fun getTimedNotesFlowSorted(experienceId: Int): Flow<List<TimedNote>>

    fun getExperienceFlow(experienceId: Int): Flow<Experience?>

    suspend fun getLatestIngestionOfEverySubstanceSinceDate(instant: Instant): List<Ingestion>

    suspend fun getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted(): List<ExperienceWithIngestionsTimedNotesAndRatings>

    suspend fun getExperiencesWithIngestionsTimedNotesAndRatingsInRange(
        fromInstant: Instant,
        toInstant: Instant
    ): List<ExperienceWithIngestionsTimedNotesAndRatings>

    suspend fun getAllCustomUnitsSorted(): List<CustomUnit>

    suspend fun getAllCustomSubstances(): List<CustomSubstance>

    suspend fun getAllSubstanceCompanions(): List<SubstanceCompanion>

    suspend fun getTimedNotes(experienceId: Int): List<TimedNote>

    suspend fun delete(substanceCompanion: SubstanceCompanion)

    suspend fun update(substanceCompanion: SubstanceCompanion)

    suspend fun insert(customSubstance: CustomSubstance): Int

    suspend fun delete(customSubstance: CustomSubstance)

    suspend fun update(customSubstance: CustomSubstance)

    fun getSortedIngestionsWithSubstanceCompanionsFlow(limit: Int): Flow<List<IngestionWithCompanionAndCustomUnit>>

    fun getSortedIngestions(limit: Int): Flow<List<Ingestion>>

    fun getSortedIngestionsFlow(): Flow<List<Ingestion>>

    fun getSortedIngestionsFlow(substanceName: String, limit: Int): Flow<List<Ingestion>>

    fun getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName: String): Flow<List<IngestionWithExperienceAndCustomUnit>>

    fun getAllSubstanceCompanionsFlow(): Flow<List<SubstanceCompanion>>

    fun getCustomUnitsFlow(isArchived: Boolean): Flow<List<CustomUnit>>

    fun getUnArchivedCustomUnitsFlow(substanceName: String): Flow<List<CustomUnit>>

    fun getAllCustomUnitsFlow(): Flow<List<CustomUnit>>

    fun getSubstanceCompanionFlow(substanceName: String): Flow<SubstanceCompanion?>
}
