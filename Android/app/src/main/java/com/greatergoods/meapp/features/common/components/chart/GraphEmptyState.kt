package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeTheme

/**
 * Internal composable for displaying the empty state of the graph.
 */
@Composable
internal fun GraphEmptyState(modifier: Modifier, placeHolder: String?) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = placeHolder ?: "You haven't added \n any entries.",
            modifier = Modifier.padding(16.dp),
            color = MeTheme.colorScheme.textBody,
            style = MeTheme.typography.heading5,
            textAlign = TextAlign.Center,
            minLines = 2,
        )
    }
}
