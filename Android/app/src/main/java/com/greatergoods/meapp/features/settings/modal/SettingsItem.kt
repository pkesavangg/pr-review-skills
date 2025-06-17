package com.greatergoods.meapp.features.settings.modal

data class SettingsItem(
    val title: String,
    val value: String? = null,
    val showInfoIcon: Boolean = false,
    val onClick: () -> Unit = {},
)
