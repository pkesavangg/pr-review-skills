package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.historyDetail.modal.Stat
import com.greatergoods.meapp.theme.MeTheme

/**
 * Composable for displaying a single metric item in the dashboard metrics grid.
 */
@Composable
fun StatCard(
    stat: Stat,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onMetricClick: (Stat) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.inverseAction,
        ),
        onClick = { onMetricClick(stat) },
    ) {
        Row {
            if (stat.icon != null) {
                AppIcon(
                    id = stat.icon,
                    contentDescription = stat.label,
                    tintColor = MeTheme.colorScheme.streak,
                )
            }
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(vertical = MeTheme.spacing.sm),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stat.value ?: "---",
                    style = MeTheme.typography.heading4,
                    color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textHeading,
                )
                Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
                Text(
                    text = stat.label.plus(stat.unit),
                    style = MeTheme.typography.subHeading2,
                    color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textSubheading,
                )
            }
        }
    }
}
