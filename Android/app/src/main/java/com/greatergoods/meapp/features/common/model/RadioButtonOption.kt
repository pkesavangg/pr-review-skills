package com.greatergoods.meapp.features.common.model

/**
 * Data class representing a radio button option for use in AppRadioGroup.
 *
 * @param id Unique identifier for the option.
 * @param label Display label for the option.
 * @param enabled Whether the option is enabled.
 */
data class RadioButtonOption<T>(
    val id: T,
    val label: String,
    val enabled: Boolean = true
)
