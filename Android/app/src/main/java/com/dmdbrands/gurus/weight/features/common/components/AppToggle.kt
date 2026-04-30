package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Defaults for [AppToggle].
 */
object AppToggleDefaults {

  val defaultWidth = 52.dp

  /**
   * Creates a [SwitchColors] that represents the default colors used in an [AppToggle].
   *
   * @return The default [SwitchColors].
   */
  @Composable
  fun colors(): SwitchColors =
    SwitchDefaults.colors(
      checkedThumbColor = MeTheme.colorScheme.primaryBackground,
      checkedTrackColor = MeTheme.colorScheme.primaryAction,
      uncheckedThumbColor = MeTheme.colorScheme.iconSecondary,
      uncheckedTrackColor = MeTheme.colorScheme.utility,
      disabledCheckedThumbColor = MeTheme.colorScheme.secondaryActionDisabled,
      disabledCheckedTrackColor = MeTheme.colorScheme.secondaryActionDisabled,
      disabledUncheckedThumbColor = MeTheme.colorScheme.secondaryActionDisabled,
      disabledUncheckedTrackColor = MeTheme.colorScheme.secondaryActionDisabled,
      checkedBorderColor = MeTheme.colorScheme.primaryAction,
      uncheckedBorderColor = MeTheme.colorScheme.iconSecondary,
      disabledCheckedBorderColor = MeTheme.colorScheme.secondaryActionDisabled,
      disabledUncheckedBorderColor = MeTheme.colorScheme.secondaryActionDisabled,
    )
}

/**
 * A theme-aware Switch composable that wraps the Material3 Switch with MeAppTheme colors.
 * It provides a consistent look and feel for toggle switches across the app.
 *
 * @param checked The current state of the switch.
 * @param onCheckedChange A callback lambda that is invoked when the switch is toggled.
 * @param modifier A [Modifier] to be applied to the switch.
 * @param enabled Controls the enabled state of the switch. When false, the switch will not be
 * interactive.
 */
@Composable
fun AppToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    enabled = enabled,
    colors = AppToggleDefaults.colors(),
    modifier = modifier.widthIn(
      min = AppToggleDefaults.defaultWidth,
      max = AppToggleDefaults.defaultWidth,
    ),
  )
}

/**
 * Previews for the [AppToggle] composable in various states.
 * This includes previews for light and dark themes.
 */
@PreviewTheme
@Composable
fun AppTogglePreview() {
  MeAppTheme {
    Column(verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.md)) {
      AppToggle(checked = true, onCheckedChange = {})
      AppToggle(checked = false, onCheckedChange = {})
      AppToggle(checked = true, onCheckedChange = {}, enabled = false)
      AppToggle(checked = false, onCheckedChange = {}, enabled = false)
    }
  }
}
