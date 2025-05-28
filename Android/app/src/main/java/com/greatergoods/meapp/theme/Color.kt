package com.greatergoods.meapp.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.greatergoods.meapp.theme.model.Colors
import com.greatergoods.meapp.theme.token.DarkColorToken
import com.greatergoods.meapp.theme.token.LightColorToken

// 3. Use semantic tokens for the light theme
val LightColors = Colors(
    background = LightColorToken.background,
    primaryDisabled = LightColorToken.errorDisabled,
    secondary = LightColorToken.secondary,

    overlay = LightColorToken.overlay,
    toastBackground = LightColorToken.toastBackground,


    primaryAction = LightColorToken.primaryAction,
    disabledState = LightColorToken.primaryActionDisabled,
    secondaryAction = LightColorToken.secondaryAction,
    secondaryDisabled = LightColorToken.secondaryDisabled,

    heading = LightColorToken.heading,
    body = LightColorToken.body,
    subheading = LightColorToken.subheading,
    error = LightColorToken.error,
    disabled = LightColorToken.errorDisabled,
    inverse = LightColorToken.inverse,
    inverseSecondary = LightColorToken.inverseSecondary,

    goal = LightColorToken.goal,
    streak = LightColorToken.streak,
    utility = LightColorToken.utility,

    brand = LightColorToken.brand
)

val DarkColors = Colors(
    background = DarkColorToken.background,
    primaryDisabled = DarkColorToken.errorDisabled,
    secondary = DarkColorToken.secondary,

    overlay = DarkColorToken.overlay,
    toastBackground = DarkColorToken.toastBackground,

    primaryAction = DarkColorToken.primaryAction,
    disabledState = DarkColorToken.primaryActionDisabled,
    secondaryAction = DarkColorToken.secondaryAction,
    secondaryDisabled = DarkColorToken.secondaryDisabled,

    heading = DarkColorToken.heading,
    body = DarkColorToken.body,
    subheading = DarkColorToken.subheading,
    error = DarkColorToken.error,
    disabled = DarkColorToken.errorDisabled,
    inverse = DarkColorToken.inverse,
    inverseSecondary = DarkColorToken.inverseSecondary,

    goal = DarkColorToken.goal,
    streak = DarkColorToken.streak,
    utility = DarkColorToken.utility,

    brand = DarkColorToken.brand
)


val LocalAppColors = staticCompositionLocalOf<Colors> { error("No AppColors provided") }


