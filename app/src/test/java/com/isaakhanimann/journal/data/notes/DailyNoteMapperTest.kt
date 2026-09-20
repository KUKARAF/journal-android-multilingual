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

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.settings.ExperienceSerializable
import com.isaakhanimann.journal.ui.tabs.settings.IngestionSerializable
import com.isaakhanimann.journal.ui.tabs.settings.LocationSerializable
import com.isaakhanimann.journal.ui.tabs.settings.RatingSerializable
import com.isaakhanimann.journal.ui.tabs.settings.TimedNoteSerializable
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyNoteMapperTest {

    private val day = LocalDate.of(2026, 9, 20)

    private fun sampleExperiences(): List<ExperienceSerializable> = listOf(
        ExperienceSerializable(
            title = "Morning: coffee & \"notes\"",
            text = "First experience body.\nSecond line with a colon: value.",
            creationDate = Instant.ofEpochMilli(1_700_000_000_000),
            sortDate = Instant.ofEpochMilli(1_700_000_100_000),
            isFavorite = true,
            ingestions = listOf(
                IngestionSerializable(
                    substanceName = "Caffeine",
                    time = Instant.ofEpochMilli(1_700_000_050_000),
                    endTime = null,
                    creationDate = Instant.ofEpochMilli(1_700_000_040_000),
                    administrationRoute = AdministrationRoute.ORAL,
                    dose = 100.0,
                    isDoseAnEstimate = false,
                    estimatedDoseStandardDeviation = null,
                    units = "mg",
                    notes = "with breakfast",
                    stomachFullness = null,
                    consumerName = null,
                    customUnitId = null,
                    releaseForm = null
                )
            ),
            location = LocationSerializable(name = "Home", latitude = 52.2, longitude = 21.0),
            ratings = listOf(
                RatingSerializable(
                    option = ShulginRatingOption.PLUS,
                    time = Instant.ofEpochMilli(1_700_000_060_000),
                    creationDate = Instant.ofEpochMilli(1_700_000_055_000)
                )
            ),
            timedNotes = listOf(
                TimedNoteSerializable(
                    creationDate = Instant.ofEpochMilli(1_700_000_070_000),
                    time = Instant.ofEpochMilli(1_700_000_075_000),
                    note = "feeling alert",
                    color = AdaptiveColor.RED,
                    isPartOfTimeline = true
                )
            )
        ),
        ExperienceSerializable(
            title = "Evening",
            text = "A second experience on the same day.",
            creationDate = Instant.ofEpochMilli(1_700_030_000_000),
            sortDate = Instant.ofEpochMilli(1_700_030_100_000),
            isFavorite = false,
            ingestions = emptyList(),
            location = null,
            ratings = emptyList(),
            timedNotes = emptyList()
        )
    )

    @Test
    fun markdownHasFrontmatterAndExperiencesKey() {
        val markdown = DailyNoteMapper.toMarkdown(day, sampleExperiences())
        assertTrue("must start with a YAML fence", markdown.startsWith("---\n"))
        assertTrue("must carry the experiences list key", markdown.contains("\nexperiences: "))
        assertTrue("body renders a title heading", markdown.contains("# Evening"))
    }

    @Test
    fun multiExperienceDayRoundTrips() {
        val original = sampleExperiences()
        val markdown = DailyNoteMapper.toMarkdown(day, original)
        val parsed = DailyNoteMapper.parseExperiences(markdown)
        assertEquals(original, parsed)
    }

    @Test
    fun emptyDayRoundTripsToEmptyList() {
        val markdown = DailyNoteMapper.toMarkdown(day, emptyList())
        assertEquals(emptyList<ExperienceSerializable>(), DailyNoteMapper.parseExperiences(markdown))
    }

    @Test
    fun dayIdUsesDiaryPrefix() {
        assertEquals("diary/2026-09-20", DailyNoteMapper.dayId(day))
    }
}
