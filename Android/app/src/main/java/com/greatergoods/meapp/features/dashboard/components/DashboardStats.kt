package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.dashboard.strings.DashboardStatsStrings
import com.greatergoods.meapp.features.historyDetail.helper.MetricHelper
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Composable for displaying dashboard stats in a 2-column grid, matching Figma design.
 * All static text is referenced from DashboardStatsStrings.
 */
@Composable
fun DashboardStats(
    lbsToGoal: String,
    lbsToGoalLabel: String,
    goalProgress: Float,
    startWeight: String,
    goalWeight: String,
    modifier: Modifier = Modifier,
) {
    val mileStones = MetricHelper.getMilestone()
    Column(modifier = modifier) {
        // Top card: lbs to goal with progress bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MeTheme.spacing.sm),
            colors = CardDefaults.cardColors(containerColor = MeTheme.colorScheme.primaryBackground),
        ) {
            Column(
                modifier = Modifier.padding(MeTheme.spacing.md),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = lbsToGoal,
                        color = MeTheme.colorScheme.textHeading,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.size(MeTheme.spacing.xs))
                    Text(
                        text = lbsToGoalLabel,
                        style = MeTheme.typography.body2,
                        color = MeTheme.colorScheme.textSubheading,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    color = MeTheme.colorScheme.success,
                )
                Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = startWeight,
                        style = MeTheme.typography.body2,
                        color = MeTheme.colorScheme.textSubheading,
                    )
                    Text(
                        text = goalWeight,
                        style = MeTheme.typography.body2,
                        color = MeTheme.colorScheme.textSubheading,
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            userScrollEnabled = false,
                modifier = Modifier . fillMaxWidth ().height(400.dp),
            horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
            items(mileStones) {
                StatCard(stat = it, isSelected = false) { }
            }
        }
    }
}

@PreviewTheme
@Composable
private fun DashboardStatsPreview() {
    MeAppTheme {
        DashboardStats(
            lbsToGoal = "-13.2",
            lbsToGoalLabel = DashboardStatsStrings.LbsToGoal,
            goalProgress = 0.7f,
            startWeight = "154.3 lbs",
            goalWeight = "132.3 lbs",
        )
    }
}
