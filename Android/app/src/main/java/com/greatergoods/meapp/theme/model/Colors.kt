package com.greatergoods.meapp.theme.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.theme.enums.ColorSchemeKey

@Stable
data class Colors(
    val background: Color,
    val primaryDisabled: Color,
    val secondary: Color,
    val overlay: Color,
    val toastBackground: Color,

    val primaryAction: Color,
    val disabledState: Color,
    val secondaryAction: Color,
    val secondaryDisabled: Color,

    val heading: Color,
    val body: Color,
    val subheading: Color,
    val error: Color,
    val disabled: Color,
    val inverse: Color,
    val inverseSecondary: Color,

    val goal: Color,
    val streak: Color,
    val utility: Color,

    val brand: Color
) {
    fun fromToken(token: ColorSchemeKey): Color {
        return when (token) {
            ColorSchemeKey.Background -> background
            ColorSchemeKey.PrimaryDisabled -> primaryDisabled
            ColorSchemeKey.Secondary -> secondary
            ColorSchemeKey.Overlay -> overlay
            ColorSchemeKey.ToastBackground -> toastBackground

            ColorSchemeKey.PrimaryAction -> primaryAction
            ColorSchemeKey.DisabledState -> disabledState
            ColorSchemeKey.SecondaryAction -> secondaryAction
            ColorSchemeKey.SecondaryDisabled -> secondaryDisabled

            ColorSchemeKey.Heading -> heading
            ColorSchemeKey.Subheading -> subheading
            ColorSchemeKey.Error -> error
            ColorSchemeKey.Disabled -> disabled
            ColorSchemeKey.Inverse -> inverse
            ColorSchemeKey.InverseSecondary -> inverseSecondary

            ColorSchemeKey.Goal -> goal
            ColorSchemeKey.Streak -> streak
            ColorSchemeKey.Utility -> utility

            ColorSchemeKey.Brand -> brand
        }
    }
}
