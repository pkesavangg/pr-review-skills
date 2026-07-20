package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.history.strings.HistoryEmptyStateStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Shared History empty state for the per-product / per-state cases (MOB-1221).
 *
 * Renders a product-tinted icon, title, description, and a single CTA pill. Each state
 * shows exactly one CTA: "no device connected" → ADD DEVICE; "device connected, no
 * entries yet" → LOG MANUALLY.
 */
@Composable
fun ProductHistoryEmptyState(
    icon: Int,
    iconTint: Color,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContentDescription: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            id = icon,
            contentDescription = iconContentDescription,
            tintColor = iconTint,
            modifier = Modifier.size(50.dp),
            onClick = null,
        )
        AppText(
            text = title,
            textType = TextType.Title,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MeTheme.spacing.md),
        )
        AppText(
            text = description,
            textType = TextType.Body,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                top = MeTheme.spacing.sm,
                bottom = MeTheme.spacing.lg,
                start = MeTheme.spacing.xl,
                end = MeTheme.spacing.xl,
            ),
        )
        AppButton(
            label = primaryLabel,
            modifier = Modifier.testTag(TestTags.History.EmptyStatePrimaryButton),
            onClick = onPrimaryClick,
        )
    }
}

@PreviewTheme
@Composable
private fun ProductHistoryEmptyStateNoDevicePreview() {
    MeAppTheme {
        ProductHistoryEmptyState(
            icon = AppIcons.Default.WeightScale,
            iconTint = SnapshotColors.Weight,
            title = HistoryEmptyStateStrings.WeightNoDeviceTitle,
            description = HistoryEmptyStateStrings.WeightNoDeviceDescription,
            primaryLabel = HistoryEmptyStateStrings.AddDevice,
            onPrimaryClick = {},
        )
    }
}

@PreviewTheme
@Composable
private fun ProductHistoryEmptyStateNoEntryPreview() {
    MeAppTheme {
        ProductHistoryEmptyState(
            icon = AppIcons.Default.History,
            iconTint = SnapshotColors.Baby,
            title = HistoryEmptyStateStrings.BabyNoEntryTitle,
            description = HistoryEmptyStateStrings.BabyNoEntryDescription,
            primaryLabel = HistoryEmptyStateStrings.LogManually,
            onPrimaryClick = {},
        )
    }
}
