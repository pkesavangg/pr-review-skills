package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.model.Stat
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
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = MeTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (stat.icon != null) {
                AppIcon(
                    id = stat.icon,
                    contentDescription = stat.label,
                    tintColor = MeTheme.colorScheme.streak,
                )
            }
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = buildString {
                        if (stat.valuePrefix != null) {
                            append(stat.valuePrefix)
                        }
                        if (stat.value != null) {
                            append(stat.value)
                        } else {
                            append("---")
                        }
                    },
                    style = MeTheme.typography.heading4,
                    color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textHeading,
                )
                Text(
                    text = stat.label.plus(" ").plus(stat.unit ?: "").lowercase(),
                    style = MeTheme.typography.subHeading2,
                    color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textSubheading,
                )
            }
        }
    }
}
