// AppIconButton.kt
// Defines a customizable icon button for Jetpack Compose with different color types.

package com.greatergoods.meapp.features.common.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

// Types for icon button color schemes
enum class AppIconButtonType { Primary, Secondary, Tertiary }

// Default color logic for AppIconButton
object AppIconButtonDefaults {
    /**
     * Returns icon color based on button type and enabled state.
     */
    @Composable
    fun contentColor(
        type: AppIconButtonType,
        enabled: Boolean,
    ): Color =
        when (type) {
            AppIconButtonType.Primary ->
                if (enabled) MeTheme.colorScheme.primaryAction else MeTheme.colorScheme.primaryBackgroundDisabled

            AppIconButtonType.Secondary ->
                if (enabled) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.inverseActionDisabled

            AppIconButtonType.Tertiary ->
                if (enabled) MeTheme.colorScheme.tertiaryAction else MeTheme.colorScheme.tertiaryActionDisabled
        }
}

/**
 * A customizable icon button for the app.
 * @param id Drawable resource id for the icon
 * @param type Color type for the button
 * @param contentDescription Accessibility description
 * @param enabled Whether the button is enabled
 * @param onClick Click handler
 */
@Composable
fun AppIconButton(
    @DrawableRes id: Int,
    type: AppIconButtonType = AppIconButtonType.Primary,
    contentDescription: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val iconColor = AppIconButtonDefaults.contentColor(type, enabled)
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(id),
            contentDescription = contentDescription,
            tint = iconColor,
        )
    }
}

// --- Preview Section ---
// Shows all icon button types for design review.
@PreviewTheme
@Composable
fun AppIconButtonPreview() {
    MeAppTheme {
        Column {
            AppIconButton(AppIcons.Default.Close) {}
            AppIconButton(AppIcons.Default.Close, AppIconButtonType.Secondary) {}
            AppIconButton(AppIcons.Default.Close, AppIconButtonType.Tertiary) {}
        }
    }
}
