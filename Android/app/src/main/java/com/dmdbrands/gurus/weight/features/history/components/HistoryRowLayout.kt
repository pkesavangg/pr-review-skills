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
@Composable
fun HistoryRowLayout(
  month: String,
  entryCount: Int,
  onClick: () -> Unit,
  content: @Composable RowScope.() -> Unit,
) {
  var lastClickTime by remember { mutableStateOf(0L) }
  val debounceTime = 500L

  Row(
    modifier = Modifier
      .fillMaxWidth()
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
      contentDescription = HistoryItemStrings.GoToMonthView,
      onClick = null,
    )
  }
}
