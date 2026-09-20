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

/**
 * The relevance allowlist of substance metrics shared with SoloForge over `/api/stats`. Only these
 * substances cross the bridge (nutrition metrics like kcal/salt/weight are ignored). The registry
 * definitions here MUST match SoloForge exactly (idempotent PUT = last writer wins), so both apps
 * register the same unit/label/chart/agg.
 */
data class SubstanceStat(
    val key: String,
    val substanceName: String,
    val unit: String,
    val label: String,
    val chart: String,
    val agg: String
)

object SubstanceStatsCatalog {
    val all: List<SubstanceStat> = listOf(
        SubstanceStat(key = "alcohol", substanceName = "Alcohol", unit = "g", label = "Alcohol", chart = "bar", agg = "sum"),
        SubstanceStat(key = "caffeine", substanceName = "Caffeine", unit = "mg", label = "Caffeine", chart = "line", agg = "sum"),
        SubstanceStat(key = "nicotine", substanceName = "Nicotine", unit = "mg", label = "Nicotine", chart = "bar", agg = "sum")
    )

    val metricKeys: List<String> = all.map { it.key }

    private val byKey: Map<String, SubstanceStat> = all.associateBy { it.key }
    private val byName: Map<String, SubstanceStat> = all.associateBy { it.substanceName.lowercase() }

    fun forKey(key: String): SubstanceStat? = byKey[key.lowercase()]

    fun forSubstanceName(name: String): SubstanceStat? = byName[name.lowercase()]
}
