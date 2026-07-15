package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.exposeTestTagsAsResourceId
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

/**
 * Shared bottom sheet wrapper used across the app — analogous to [AppScaffold] but for
 * `ModalBottomSheet`. Provides a consistent header (compact drag handle + absolutely-centered
 * title + leading/trailing slots) and exposes a body slot for the consumer.
 *
 * Matches Figma's "Navigation Bar — Stacked" pattern (node 26501:378232): the title is
 * absolute-centered regardless of the leading or trailing widgets — not pushed off-center
 * by the close button.
 *
 * Default behaviour:
 * - Drag handle: compact 36×5dp pill (`utility` color), tight 8dp/4dp vertical padding.
 * - Leading: close `X` icon that calls [onDismiss] after animating the sheet shut.
 * - Container: `primaryBackground` (white). Body that needs a beige surface should paint it.
 *
 * @param title Centered title shown in the header bar.
 * @param onDismiss Invoked on scrim tap, drag-down dismiss, or close-button tap.
 * @param navigationIcon Leading slot. Pass `null` (default) to render the standard close `X`
 *   wired to [onDismiss]; pass `{}` to render nothing; pass a custom lambda to override.
 * @param actions Optional trailing icons/buttons.
 * @param containerColor Sheet background. Body content can paint over it for two-tone designs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
  title: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: (@Composable () -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
  containerColor: Color = MeTheme.colorScheme.primaryBackground,
  scrimColor: Color = MeTheme.colorScheme.overlay,
  skipPartiallyExpanded: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
  val scope = rememberCoroutineScope()

  val close: () -> Unit = {
    scope.launch {
      sheetState.hide()
      onDismiss()
    }
    Unit
  }

  val resolvedNavigationIcon: @Composable () -> Unit =
    navigationIcon ?: { DefaultCloseButton(onClick = close) }

  ModalBottomSheet(
    sheetState = sheetState,
    modifier = modifier
      .systemBarsPadding()
      .navigationBarsPadding()
      // Bottom sheet is a separate window; opt its subtree into testTag→resource-id exposure
      // so tagged controls are Appium-visible (DEBUG-gated). See MOB-1503 / MOB-1099.
      .exposeTestTagsAsResourceId(),
    onDismissRequest = onDismiss,
    containerColor = containerColor,
    scrimColor = scrimColor,
    dragHandle = {
      AppBottomSheetTopBar(
        title = title,
        navigationIcon = resolvedNavigationIcon,
        actions = actions,
      )
    },
    content = content,
  )
}

/**
 * The "top bar" of an [AppBottomSheet] — passed to `ModalBottomSheet.dragHandle` so it sits
 * at the top of the sheet (above the content slot). Bundles the compact grabber with the
 * title bar so the entire sheet header is one cohesive piece.
 *
 * Layout (matches Figma "Navigation Bar — Stacked", node 26501:378232):
 * - Compact 36×5dp grabber pill, tight `xs (8dp)` top + `x3s (4dp)` bottom padding.
 * - 44dp title row: leading icon at start, title absolutely centered (immune to leading/
 *   trailing widget widths), trailing actions at end.
 */
@Composable
private fun AppBottomSheetTopBar(
  title: String,
  navigationIcon: @Composable () -> Unit,
  actions: @Composable RowScope.() -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    // Grabber
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = MeTheme.spacing.xs, bottom = MeTheme.spacing.x3s),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(width = 36.dp, height = 5.dp)
          .background(MeTheme.colorScheme.utility, RoundedCornerShape(2.5.dp)),
      )
    }
    // Title bar
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(44.dp),
    ) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = MeTheme.spacing.x2s),
      ) {
        navigationIcon()
      }
      Text(
        text = title,
        style = MeTheme.typography.heading5,
        color = MeTheme.colorScheme.textHeading,
        modifier = Modifier
          .align(Alignment.Center)
          .testTag(TestTags.BottomSheet.Title),
      )
      Row(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = MeTheme.spacing.x2s),
        verticalAlignment = Alignment.CenterVertically,
        content = actions,
      )
    }
  }
}

@Composable
private fun DefaultCloseButton(onClick: () -> Unit) {
  AppIconButton(
    id = AppIcons.Default.Close,
    modifier = Modifier.testTag(TestTags.BottomSheet.CloseButton),
    contentDescription = AppBottomSheetStrings.Close,
    onClick = onClick,
  )
}

private object AppBottomSheetStrings {
  const val Close = "Close"
}
