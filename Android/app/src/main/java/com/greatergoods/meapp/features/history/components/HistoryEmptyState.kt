package com.greatergoods.meapp.features.history.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.history.strings.HistoryScreenStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Empty state for the history screen, shown when there are no history items.
 */
@Composable
fun HistoryEmptyState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = HistoryScreenStrings.EmptyStateTitle,
            textType = TextType.Title,
        )
        AppText(
            text = HistoryScreenStrings.EmptyStateDescription,
            textType = TextType.Body,
            modifier = Modifier.padding(top = MeTheme.spacing.sm, bottom = MeTheme.spacing.lg),
        )
        AppButton(
            label = HistoryScreenStrings.ConnectScale,
            onClick = onRetry,
        )
    }
}

@PreviewTheme
@Composable
fun HistoryEmptyStatePreview() {
    MeAppTheme {
        HistoryEmptyState(onRetry = {})
    }
}
