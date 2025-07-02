package com.greatergoods.meapp.features.common.strings

import com.greatergoods.meapp.features.common.helper.DashboardKey

object MetricLabels {
    data class LabelPair(val full: String, val short: String? = null)

    private val labels = mapOf(
        DashboardKey.BMI to LabelPair("Body Mass Index", "BMI"),
        DashboardKey.BODY_FAT to LabelPair("Body Fat"),
        DashboardKey.MUSCLE_MASS to LabelPair("Muscle Mass", "Muscle"),
        DashboardKey.BODY_WATER to LabelPair("Body Water", "Water"),
        DashboardKey.HEART_RATE to LabelPair("Heart Rate", "Heart"),
        DashboardKey.BONE_MASS to LabelPair("Bone Mass", "Bone"),
        DashboardKey.VISCERAL_FAT to LabelPair("Visceral Fat"),
        DashboardKey.SUBCUTANEOUS_FAT to LabelPair("Subcutaneous Fat", "Sub Fat"),
        DashboardKey.PROTEIN to LabelPair("Protein", "Protein"),
        DashboardKey.SKELETAL_MUSCLE to LabelPair("Skeletal Muscle", "Skel Muscle"),
        DashboardKey.BMR to LabelPair("Basal Metabolic Rate", "BMR"),
        DashboardKey.METABOLIC_AGE to LabelPair("Metabolic Age", "Meta Age"),
    )

    fun getLabel(key: DashboardKey, useShort: Boolean): String {
        val pair = labels[key]

        return when {
            useShort && pair?.short != null -> pair.short
            !useShort && pair != null -> pair.full
            else -> key.name.replace('_', ' ')
                .lowercase()
                .split(' ')
                .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        }
    }
}

