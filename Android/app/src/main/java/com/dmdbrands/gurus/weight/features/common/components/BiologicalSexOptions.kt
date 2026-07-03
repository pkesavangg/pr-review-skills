package com.dmdbrands.gurus.weight.features.common.components

import com.dmdbrands.gurus.weight.domain.enums.BabySex

/**
 * Canonical biological-sex picker options, shared by the signup Add-Baby step and the
 * Settings → My Kids Add/Edit Baby screen so both show the same labels
 * (Male / Female / Private) and map to the same [BabySex] API values.
 */
object BiologicalSexOptions {
    const val Title = "Biological Sex"

    /** Display labels, ordered Male, Female, Private. */
    val labels: List<String> = listOf("Male", "Female", "Private")

    /** Radio options keyed by their own label (both pickers store the selected label). */
    fun options(): List<RadioButtonOption<String>> =
        labels.map { RadioButtonOption(id = it, label = it) }

    /** Maps a picker label (e.g. "Private") to its [BabySex]; null when nothing is selected. */
    fun toBabySex(label: String?): BabySex? =
        label?.takeIf { it.isNotBlank() }?.let { BabySex.fromValue(it.lowercase()) }

    /** Maps a [BabySex] back to its display label (e.g. PRIVATE -> "Private"). */
    fun toLabel(sex: BabySex?): String =
        sex?.value?.replaceFirstChar { it.uppercase() } ?: ""
}
