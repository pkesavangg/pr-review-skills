package com.dmdbrands.gurus.weight.features.dashboard.snapshot.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun SnapshotCardContainer(
  modifier: Modifier = Modifier,
  onClickLabel: String? = null,
  onTap: () -> Unit,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier
      .semantics(mergeDescendants = true) {}
      .fillMaxWidth()
      .clip(RoundedCornerShape(MeTheme.borderRadius.md))
      .background(MeTheme.colorScheme.primaryBackground)
      .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onTap)
      .padding(vertical = MeTheme.spacing.sm),
  ) {
    content()
  }
}
