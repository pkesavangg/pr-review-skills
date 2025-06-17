package com.greatergoods.meapp.features.common.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme

enum class AppIconType {
    Primary,
    Secondary,
    Tertiary,
    Danger,
    Default,
}

object AppIconDefaults {
    @Composable
    fun tintColor(type: AppIconType): Color =
        when (type) {
            AppIconType.Primary -> MeAppTheme.colorScheme.primaryAction
            AppIconType.Secondary -> MeAppTheme.colorScheme.inverse
            AppIconType.Tertiary -> MeAppTheme.colorScheme.tertiaryAction
            AppIconType.Danger -> MeAppTheme.colorScheme.textError
            AppIconType.Default -> Color.Unspecified
        }
}

@Composable
fun AppIcon(
    @DrawableRes id: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
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
    val tintColor = AppIconDefaults.tintColor(type)
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
