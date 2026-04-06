package com.dmdbrands.gurus.weight.features.dashboard.snapshot.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel.DashboardSnapshotViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

object SnapshotColors {
    val Weight = Color(0xFF1565C0)
    val BloodPressure = Color(0xFF458239)
    val Baby = Color(0xFF8841A4)
}

@Composable
fun WeightSnapshotCard(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<DashboardSnapshotViewModel>()
    val state by viewModel.state.collectAsState()

    SnapshotCardContainer(modifier = modifier, onTap = onTap) {
        val chart = state.weight

        Text(
            text = DashboardSnapshotStrings.WeekAverage,
            style = MeTheme.typography.subHeading1,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
        )

        Row(verticalAlignment = Alignment.Bottom ,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm)
        ){
            Text(
                text = chart.label.ifEmpty { DashboardSnapshotStrings.PlaceholderDash },
                color = SnapshotColors.Weight,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = " ${state.weightUnit.label}",
                style = MeTheme.typography.subHeading2,
                color = MeTheme.colorScheme.textSubheading,
                modifier = Modifier.padding(bottom = MeTheme.spacing.xs),
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        if (chart.startTimestamp != null && chart.endTimestamp != null) {
            SnapshotLineChart(
                modelProducer = viewModel.weightModelProducer,
                lineColor = SnapshotColors.Weight,
                startTimestamp = chart.startTimestamp,
                endTimestamp = chart.endTimestamp,
                yStep = chart.yStep,
                yMin = chart.yMin,
                yMax = chart.yMax,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun BpSnapshotCard(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnapshotCardContainer(modifier = modifier, onTap = onTap) {
        Row {
            Text(
                text = DashboardSnapshotStrings.Mmhg,
                style = MeTheme.typography.subHeading1,
                color = MeTheme.colorScheme.textSubheading,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = DashboardSnapshotStrings.Pulse,
                style = MeTheme.typography.subHeading1,
                color = MeTheme.colorScheme.textSubheading,
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = DashboardSnapshotStrings.PlaceholderDash,
                color = SnapshotColors.BloodPressure,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = DashboardSnapshotStrings.PlaceholderDash,
                color = MeTheme.colorScheme.textSubheading,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        PlaceholderGraphBox(text = "BP graph coming soon")
    }
}

@Composable
fun BabySnapshotCard(
    product: ProductSelection.Baby,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnapshotCardContainer(modifier = modifier, onTap = onTap) {
        Text(
            text = "${product.profile.name}'s ${DashboardSnapshotStrings.Weight}",
            style = MeTheme.typography.subHeading1,
            color = MeTheme.colorScheme.textSubheading,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))

        Text(
            text = DashboardSnapshotStrings.PlaceholderDash,
            color = SnapshotColors.Baby,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        PlaceholderGraphBox(text = "Baby graph coming soon")
    }
}

@PreviewTheme
@Composable
private fun BpSnapshotCardPreview() {
    MeAppTheme {
        BpSnapshotCard(onTap = {})
    }
}
