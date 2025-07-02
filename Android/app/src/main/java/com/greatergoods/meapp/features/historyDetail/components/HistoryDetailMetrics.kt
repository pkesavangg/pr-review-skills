package com.greatergoods.meapp.features.historyDetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric.Companion.fromScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.historyDetail.helper.MetricHelper
import com.greatergoods.meapp.features.historyDetail.helper.MetricHelper.getMetrics
import com.greatergoods.meapp.features.historyDetail.modal.Metric
import com.greatergoods.meapp.theme.MeTheme
import android.util.Log

@Composable
internal fun MetricItem(
    metric: Metric,
    modifier: Modifier = Modifier,
    index: Int,
    size: Int = 1,
) {
    val bgColor = MetricHelper.getBgColor(index, size)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(all = MeTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = metric.label,
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textBody,
        )
        Row {
            Text(
                text = metric.value.plus(metric.unit),
                style = MeTheme.typography.body2,
                color = MeTheme.colorScheme.textBody,
            )
            Spacer(modifier = Modifier.width(MeTheme.spacing.x2s))
            AppIcon(
                id = metric.icon,
                contentDescription = metric.label,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryDetailItemDetails(
    item: ScaleEntry,
) {
    val metrics = getMetrics(fromScaleEntry(item))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MeTheme.colorScheme.primaryBackground),
    ) {
        metrics.forEachIndexed { index, metric ->
            MetricItem(
                metric = metric,
                index = index,
                size = metrics.size,
            )
        }
        if (metrics.size % 2 != 0) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MeTheme.colorScheme.utility,
            )
        }
    }
}
