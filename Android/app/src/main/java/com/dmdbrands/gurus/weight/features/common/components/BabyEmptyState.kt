package com.dmdbrands.gurus.weight.features.common.components

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.strings.BabyEmptyStateStrings
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Shared empty state shown on Dashboard / Manual Entry / History when the account
 * owns a baby scale but has no baby profiles (e.g. deleted their last baby). Offers
 * an `ADD A BABY` CTA that routes to the add-baby flow. (MOB-416)
 */
@Composable
fun BabyEmptyState(
    onAddBaby: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = BabyEmptyStateStrings.Description,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BabyEmptyContent(onAddBaby = onAddBaby, description = description)
    }
}

/**
 * The icon + title + description + ADD A BABY CTA, without any sizing/scroll wrapper.
 * Reused full-screen (Dashboard/Entry/History) and inside the snapshot empty card. (MOB-592)
 */
@Composable
fun BabyEmptyContent(
    onAddBaby: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = BabyEmptyStateStrings.Description,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            id = AppIcons.Default.Baby,
            contentDescription = BabyEmptyStateStrings.IconContentDescription,
            tintColor = SnapshotColors.Baby,
            modifier = Modifier.size(50.dp),
            onClick = null,
        )
        AppText(
            text = BabyEmptyStateStrings.Title,
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
            label = BabyEmptyStateStrings.AddABaby,
            onClick = onAddBaby,
        )
    }
}

@PreviewTheme
@Composable
fun BabyEmptyStatePreview() {
    MeAppTheme {
        BabyEmptyState(onAddBaby = {})
    }
}
