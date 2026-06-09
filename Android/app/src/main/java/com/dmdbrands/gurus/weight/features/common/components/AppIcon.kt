package com.dmdbrands.gurus.weight.features.common.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

enum class AppIconType {
    Primary,
    Secondary,
    Tertiary,
    Danger,
    Inverse,
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
                if (enabled) MeTheme.colorScheme.secondaryAction
                else MeTheme.colorScheme.secondaryActionDisabled
            AppIconType.Inverse ->
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
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tintColor: Color? = null,
    type: AppIconType = AppIconType.Primary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = {},
) {
    val modifier =
      if (onClick != null)
        modifier
            .debounceClick(
                enabled = enabled,
            ) { onClick.invoke() }
  else modifier
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
            AppIcon(id = AppIcons.Default.Close, contentDescription = "Preview", type = AppIconType.Inverse)
            AppIcon(id = AppIcons.Default.Close, contentDescription = "Preview", type = AppIconType.Tertiary)
            AppIcon(id = AppIcons.Default.Close, contentDescription = "Preview", type = AppIconType.Danger)
        }
    }
}
