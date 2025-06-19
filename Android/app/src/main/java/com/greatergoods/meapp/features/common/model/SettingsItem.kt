package com.greatergoods.meapp.features.common.model

import androidx.compose.runtime.Composable

enum class SettingColorType {
    Primary,
    Tertiary,
    Danger,
    Default,
}

sealed class SettingsItemType {
    // Text alone - No params
    data class TextOnly(
        val text: String,
    ) : SettingsItemType()

    // Text with action Icon (Default) - Text param
    data class Action(
        val text: String? = null,
    ) : SettingsItemType()

    // No data -> NO param
    data object None : SettingsItemType()

    // Text with custom icon -> text and icon param
    data class CustomIcon(
        val text: String,
        val icon: @Composable () -> Unit,
    ) : SettingsItemType()

    // Dropdown (Text and dropdown icon) -> Text param
    data class Dropdown(
        val text: String,
    ) : SettingsItemType()

    // Custom compose -> compose param
    data class Custom(
        val content: @Composable () -> Unit,
    ) : SettingsItemType()
}

data class SettingsItem(
    val title: String,
    val type: SettingsItemType = SettingsItemType.Action(),
    val color: SettingColorType = SettingColorType.Default,
    val onClick: () -> Unit = {},
)
