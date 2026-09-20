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

import com.isaakhanimann.journal.ui.tabs.settings.ExperienceSerializable
import java.time.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts the experiences bucketed on a single local day to/from a daily note: a markdown file
 * with a leading YAML frontmatter block (`---\n...\n---\n`) followed by a human-readable body.
 *
 * Multi-experience-per-day handling: a day can hold more than one experience, so the structured
 * payload is always a YAML list under an `experiences:` key. The list value is emitted as a JSON
 * array on a single line — because JSON is a strict subset of YAML this is valid frontmatter, yet
 * it lets us round-trip the full [ExperienceSerializable] graph (title/text/favorite/location plus
 * ingestions/ratings/timedNotes) losslessly via kotlinx.serialization. The body renders each
 * experience's title as a heading followed by its text, for humans reading the note directly.
 */
object DailyNoteMapper {

    // encodeDefaults=true so every Instant and flag is written, guaranteeing a lossless round-trip.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val OPEN_FENCE = "---"
    private const val EXPERIENCES_KEY = "experiences:"

    // The frontmatter keys this app owns. On a merge these are overwritten; every other key
    // (SoloForge's kcal/vegan/workout/salt_g and the alcohol/caffeine/nicotine stat lines) is kept.
    private val OWNED_KEYS = setOf("date", "experienceCount", "titles", "experiences")

    /** Server id / path for a given day, e.g. `diary/2026-09-20`. */
    fun dayId(date: LocalDate): String = "diary/$date"

    /** Renders a fresh daily note (no foreign frontmatter to preserve). */
    fun toMarkdown(date: LocalDate, experiences: List<ExperienceSerializable>): String =
        render(date, experiences, existingMarkdown = null)

    /**
     * Renders the daily note, MERGING into [existingMarkdown] when provided: this app's own keys
     * are overwritten and every other frontmatter key is preserved verbatim, so a write from the
     * journal never clobbers SoloForge's aggregate keys or the shared substance stat lines.
     */
    fun render(
        date: LocalDate,
        experiences: List<ExperienceSerializable>,
        existingMarkdown: String?
    ): String {
        val foreignLines = existingMarkdown?.let { foreignFrontmatterLines(it) } ?: emptyList()
        return buildString {
            append(OPEN_FENCE).append('\n')
            append("date: ").append(jsonScalar(date.toString())).append('\n')
            append("experienceCount: ").append(experiences.size).append('\n')
            if (experiences.isNotEmpty()) {
                append("titles:\n")
                experiences.forEach { experience ->
                    append("  - ").append(jsonScalar(experience.title)).append('\n')
                }
            }
            append(EXPERIENCES_KEY).append(' ').append(json.encodeToString(experiences)).append('\n')
            foreignLines.forEach { append(it).append('\n') }
            append(OPEN_FENCE).append('\n')
            append('\n')
            experiences.forEachIndexed { index, experience ->
                append("# ").append(experience.title).append("\n\n")
                append(experience.text)
                if (index != experiences.lastIndex) {
                    append("\n\n")
                }
            }
            append('\n')
        }
    }

    // Returns the frontmatter lines of [markdown] with this app's own key blocks removed, so the
    // remaining foreign blocks (including their indented continuation lines) can be re-emitted.
    private fun foreignFrontmatterLines(markdown: String): List<String> {
        val normalized = markdown.replace("\r\n", "\n")
        if (!normalized.startsWith(OPEN_FENCE)) return emptyList()
        val afterOpen = normalized.substringAfter("$OPEN_FENCE\n", "")
        if (!afterOpen.contains("\n$OPEN_FENCE")) return emptyList()
        val frontMatter = afterOpen.substringBefore("\n$OPEN_FENCE")
        val lines = frontMatter.split("\n")
        val kept = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val isTopLevelKey = !line.first().isWhitespace() && TOP_LEVEL_KEY.containsMatchIn(line)
            if (isTopLevelKey) {
                val key = line.substringBefore(':').trim()
                val block = mutableListOf(line)
                var j = i + 1
                while (j < lines.size && lines[j].isNotEmpty() && lines[j].first().isWhitespace()) {
                    block += lines[j]
                    j++
                }
                if (key !in OWNED_KEYS) kept += block
                i = j
            } else {
                kept += line
                i++
            }
        }
        return kept
    }

    private val TOP_LEVEL_KEY = Regex("^[A-Za-z0-9_.-]+:")

    /** Extracts the structured experiences from the YAML frontmatter of a daily note. */
    fun parseExperiences(markdown: String): List<ExperienceSerializable> {
        val normalized = markdown.replace("\r\n", "\n")
        if (!normalized.startsWith(OPEN_FENCE)) return emptyList()
        val afterOpen = normalized.substringAfter("$OPEN_FENCE\n", "")
        val frontMatter = afterOpen.substringBefore("\n$OPEN_FENCE", afterOpen)
        val experiencesLine = frontMatter.lineSequence()
            .firstOrNull { it.startsWith(EXPERIENCES_KEY) }
            ?: return emptyList()
        val payload = experiencesLine.removePrefix(EXPERIENCES_KEY).trim()
        if (payload.isEmpty()) return emptyList()
        return json.decodeFromString(payload)
    }

    // A JSON-encoded string is also a valid YAML double-quoted scalar, so this safely escapes
    // colons, quotes and other YAML-significant characters in titles/dates.
    private fun jsonScalar(value: String): String = json.encodeToString(value)
}
