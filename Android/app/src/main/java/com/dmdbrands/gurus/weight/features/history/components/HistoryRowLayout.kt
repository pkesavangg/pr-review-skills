package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Shared shell layout for history rows. Provides:
 * - Debounced click handling
 * - Left column: month/year + entry count
 * - Content slot for product-specific data columns
 * - Right chevron icon
 */
@Suppress("LongMethod")
@Composable
fun HistoryRowLayout(
  month: String,
  entryCount: Int,
  onClick: () -> Unit,
  rowContentDescription: String? = null,
  content: @Composable RowScope.() -> Unit,
) {
  var lastClickTime by remember { mutableStateOf(0L) }
  val debounceTime = 500L

  // TalkBack: collapse the month + the product value columns + the decorative chevron
  // into one focusable node so the row is read as a single coherent announcement,
  // using the caller-supplied summary when provided. The row performs the navigation, so
  // expose it as a Button (mergeDescendants merges children's semantics in, so the chevron's
  // own contentDescription is suppressed below to avoid reading the action twice).
  val rowSemantics = if (rowContentDescription != null) {
    Modifier.semantics(mergeDescendants = true) {
      contentDescription = rowContentDescription
      role = Role.Button
    }
  } else {
    Modifier
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(TestTags.History.MonthRow)
      .then(rowSemantics)
      .combinedClickable(
        onClick = {
          val currentTime = android.os.SystemClock.elapsedRealtime()
          if (currentTime - lastClickTime >= debounceTime) {
            lastClickTime = currentTime
            onClick()
          }
        },
        onLongClick = { /* Prevent long press navigation */ },
      )
      .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Content: month + product columns, spread evenly across the available width.
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      // Left: month + entry count
      Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
      ) {
        Text(
          text = month,
          style = MeTheme.typography.heading5,
          color = MeTheme.colorScheme.textBody,
        )
        Text(
          text = "$entryCount ${HistoryItemStrings.Entries}",
          style = MeTheme.typography.subHeading2,
          color = MeTheme.colorScheme.textSubheading,
          modifier = Modifier.padding(top = MeTheme.spacing.x2s),
        )
      }

      content()
    }

    // Right chevron — explicit 32dp gap so it stays flush regardless of inner-row width.
    AppIcon(
      modifier = Modifier.padding(start = MeTheme.spacing.lg),
      id = AppIcons.Default.RightCaret,
      // Decorative when the row is merged into one Button announcement (rowContentDescription
      // supplied): the merged row already conveys the navigation, so the chevron must not be
      // read again. When the row is not merged, keep the label as the navigation affordance.
      contentDescription = if (rowContentDescription != null) null else HistoryItemStrings.GoToMonthView,
      onClick = null,
    )
  }
}
