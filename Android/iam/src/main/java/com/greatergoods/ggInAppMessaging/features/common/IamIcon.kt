package com.greatergoods.ggInAppMessaging.features.common

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
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.ggInAppMessaging.features.resources.AppIcons
import com.greatergoods.ggInAppMessaging.theme.IamTheme

enum class AppIconType {
  Primary,
  Secondary,
  Tertiary,
  Danger,
  Default,
}

object AppIconDefaults {
  @Composable
  fun tintColor(type: AppIconType, enabled: Boolean = true): Color {
    val colors = IamTheme.colors

    return when (type) {
      AppIconType.Primary ->
        if (enabled) colors.iconPrimary
        else colors.iconPrimaryDisabled

      AppIconType.Secondary ->
        if (enabled) colors.iconSecondary
        else colors.iconSecondaryDisabled

      AppIconType.Tertiary ->
        if (enabled) colors.textHeading
        else colors.textHeading.copy(alpha = 0.5f)

      AppIconType.Danger ->
        if (enabled) Color.Red // Fallback for danger since IAM colors don't have error colors
        else Color.Red.copy(alpha = 0.5f)

      AppIconType.Default -> Color.Unspecified
    }
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

@Preview
@Composable
fun AppIconPreview() {
  Column {
    // Using Android system icons for preview since we don't have IAM-specific icons yet
    AppIcon(id = android.R.drawable.ic_menu_close_clear_cancel, contentDescription = "Preview")
    AppIcon(
      id = android.R.drawable.ic_menu_close_clear_cancel,
      contentDescription = "Preview",
      type = AppIconType.Secondary,
    )
    AppIcon(
      id = AppIcons.Logo,
      contentDescription = "Preview",
      type = AppIconType.Tertiary,
    )
    AppIcon(
      id = android.R.drawable.ic_menu_close_clear_cancel,
      contentDescription = "Preview",
      type = AppIconType.Danger,
    )
  }
}
