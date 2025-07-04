package com.greatergoods.meapp.features.common.strings

import com.greatergoods.meapp.proto.MetricKey

object MetricLabels {
    data class LabelPair(val full: String, val short: String? = null)

    private val labels = mapOf(
        MetricKey.BMI to LabelPair("Body Mass Index", "BMI"),
        MetricKey.BODY_FAT to LabelPair("Body Fat"),
        MetricKey.MUSCLE_MASS to LabelPair("Muscle Mass", "Muscle"),
        MetricKey.BODY_WATER to LabelPair("Body Water", "Water"),
        MetricKey.HEART_RATE to LabelPair("Heart Rate", "Heart"),
        MetricKey.BONE_MASS to LabelPair("Bone Mass", "Bone"),
        MetricKey.VISCERAL_FAT to LabelPair("Visceral Fat"),
        MetricKey.SUBCUTANEOUS_FAT to LabelPair("Subcutaneous Fat", "Sub Fat"),
        MetricKey.PROTEIN to LabelPair("Protein", "Protein"),
        MetricKey.SKELETAL_MUSCLE to LabelPair("Skeletal Muscle", "Skel Muscle"),
        MetricKey.BMR to LabelPair("Basal Metabolic Rate", "BMR"),
        MetricKey.METABOLIC_AGE to LabelPair("Metabolic Age", "Meta Age"),
    )

    fun getLabel(key: MetricKey, useShort: Boolean): String {
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

