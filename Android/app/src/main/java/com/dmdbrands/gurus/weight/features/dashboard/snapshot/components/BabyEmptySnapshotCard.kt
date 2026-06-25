package com.dmdbrands.gurus.weight.features.dashboard.snapshot.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.dmdbrands.gurus.weight.features.common.components.BabyEmptyContent
import com.dmdbrands.gurus.weight.features.common.components.strings.BabyEmptyStateStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Snapshot card shown when the account owns the baby product but has no baby profile yet.
 * Mirrors the other snapshot cards' rounded container, with the baby empty content + an
 * ADD A BABY CTA (snapshot-specific copy). (MOB-592)
 */
@Composable
fun BabyEmptySnapshotCard(
  onAddBaby: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(MeTheme.borderRadius.md))
      .background(MeTheme.colorScheme.primaryBackground)
      .padding(vertical = MeTheme.spacing.lg, horizontal = MeTheme.spacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    BabyEmptyContent(
      onAddBaby = onAddBaby,
      description = BabyEmptyStateStrings.SnapshotDescription,
    )
  }
}

@PreviewTheme
@Composable
private fun BabyEmptySnapshotCardPreview() {
  MeAppTheme {
    BabyEmptySnapshotCard(onAddBaby = {})
  }
}
