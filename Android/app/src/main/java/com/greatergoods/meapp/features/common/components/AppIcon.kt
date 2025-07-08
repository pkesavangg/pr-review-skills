package com.greatergoods.meapp.features.common.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

enum class AppIconType {
    Primary,
    Secondary,
    Tertiary,
    Danger,
    Default,
}

object AppIconDefaults {
    @Composable
    fun tintColor(type: AppIconType, enabled: Boolean = true): Color =
        when (type) {
            AppIconType.Primary ->
                if (enabled) MeTheme.colorScheme.primaryAction
                else MeTheme.colorScheme.primaryActionDisabled
            AppIconType.Secondary ->
                if (enabled) MeTheme.colorScheme.inverseAction
                else MeTheme.colorScheme.inverseActionDisabled
            AppIconType.Tertiary ->
                if (enabled) MeTheme.colorScheme.tertiaryAction
                else MeTheme.colorScheme.tertiaryActionDisabled
            AppIconType.Danger ->
                if (enabled) MeTheme.colorScheme.errorAction
                else MeTheme.colorScheme.errorActionDisabled
            AppIconType.Default -> Color.Unspecified
        }
}

@Composable
fun AppIcon(
    @DrawableRes id: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tintColor: Color? = null,
    type: AppIconType = AppIconType.Primary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = {},
) {
    val modifier =
        modifier
            .clickable(
                indication = null,
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                enabled = enabled,
            ) { onClick?.invoke() }
    val tintColor = tintColor ?: AppIconDefaults.tintColor(type, enabled)
    Icon(
        painter = painterResource(id = id),
        contentDescription = contentDescription,
        tint = tintColor,
        modifier = modifier,
    )
}

@PreviewTheme
@Composable
fun AppIconPreview() {
    MeAppTheme {
        Column {
            AppIcon(id = AppIcons.Default.Close, contentDescription = "Preview")
            AppIcon(id = AppIcons.Default.Close, contentDescription = "Preview", type = AppIconType.Secondary)
            AppIcon(id = AppIcons.Default.Close, contentDescription = "Preview", type = AppIconType.Tertiary)
            AppIcon(id = AppIcons.Default.Close, contentDescription = "Preview", type = AppIconType.Danger)
        }
    }
}
