package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import com.dmdbrands.gurus.weight.domain.enums.BpSeverity
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * A single BP history entry row with expandable note.
 * Shows date/time, severity-colored pressure, pulse, and chevron.
 */
@Composable
fun BpHistoryDetailItem(
  entry: BpmEntry,
  dateDisplay: String,
  timeDisplay: String,
  isExpanded: Boolean,
  onToggle: () -> Unit,
) {
  val severity = BpSeverity.from(entry.systolic, entry.diastolic)
  val severityColor = when (severity) {
    BpSeverity.NORMAL -> MeTheme.colorScheme.success
    BpSeverity.ELEVATED -> MeTheme.colorScheme.streak
    BpSeverity.HYPERTENSION -> MeTheme.colorScheme.textError
  }
  val hasNote = !entry.note.isNullOrBlank()
  val rotation by animateFloatAsState(
    targetValue = if (isExpanded) 180f else 0f,
    label = "chevron",
  )

  Column(modifier = Modifier.fillMaxWidth()) {
    // Entry row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onToggle() }
        .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.lg),
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // Date + time
        Column {
          Text(
            text = dateDisplay,
            style = MeTheme.typography.heading5,
            color = MeTheme.colorScheme.textBody,
          )
          Text(
            text = timeDisplay,
            style = MeTheme.typography.subHeading2,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.padding(top = MeTheme.spacing.x2s),
          )
        }
        // Pressure (severity-colored)
        Column {
          Text(
            text = "${entry.systolic}/${entry.diastolic}",
            style = MeTheme.typography.heading5,
            color = severityColor,
          )
          Text(
            text = HistoryItemStrings.Mmhg,
            style = MeTheme.typography.subHeading2,
            color = MeTheme.colorScheme.textSubheading,
          )
        }
        // Pulse
        Column {
          Text(
            text = entry.pulse.toString(),
            style = MeTheme.typography.heading5,
            color = MeTheme.colorScheme.textBody,
          )
          Text(
            text = HistoryItemStrings.Pulse,
            style = MeTheme.typography.subHeading2,
            color = MeTheme.colorScheme.textSubheading,
          )
        }
      }
      // Chevron — always present for alignment, invisible if no note
      AppIcon(
        id = AppIcons.Default.ChevronDown,
        contentDescription = "notes",
        modifier = Modifier
          .rotate(rotation)
          .alpha(if (hasNote) 1f else 0f),
        onClick = if (hasNote) {
          { onToggle() }
        } else null,
      )
    }

    // Expandable note
    AnimatedVisibility(visible = isExpanded && !entry.note.isNullOrBlank()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MeTheme.spacing.sm),
      ) {
        HorizontalDivider(
          thickness = MeTheme.spacing.x6s,
          color = MeTheme.colorScheme.utility,
          modifier = Modifier.padding(bottom = MeTheme.spacing.sm),
        )
        Text(
          text = entry.note ?: "",
          style = MeTheme.typography.subHeading2,
          color = MeTheme.colorScheme.textBody,
          modifier = Modifier.padding(bottom = MeTheme.spacing.sm),
        )
      }
    }

    // Bottom divider
    HorizontalDivider(
      thickness = MeTheme.spacing.x6s,
      color = MeTheme.colorScheme.utility,
    )
  }
}
