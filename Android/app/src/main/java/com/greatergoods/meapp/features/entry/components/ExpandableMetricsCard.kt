package com.greatergoods.meapp.features.entry.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.entry.strings.EntryScreenStrings
import com.greatergoods.meapp.features.entry.viewmodel.GeneralMetricsFormControls
import com.greatergoods.meapp.features.entry.viewmodel.R4ScaleMetricsFormControls
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Expandable card for metrics section, with animated expand/collapse, title, and optional subheading.
 *
 * @param title The title of the section.
 * @param subheading Optional subheading below the title.
 * @param generalMetrics The form controls for general metrics.
 * @param r4ScaleMetrics The form controls for R4/scale metrics (optional).
 * @param expandedInitially Whether the card is expanded by default.
 */
@Composable
fun ExpandableMetricsCard(
    title: String,
    subheading: String? = null,
    generalMetrics: GeneralMetricsFormControls,
    r4ScaleMetrics: R4ScaleMetricsFormControls? = null,
    expandedInitially: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(expandedInitially) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "ChevronRotation")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MeTheme.typography.heading4)
                if (subheading != null) {
                    Text(subheading, style = MeTheme.typography.body3, color = MeTheme.colorScheme.textSubheading)
                }
            }
            Icon(
                painter = painterResource(AppIcons.Default.ChevronDown),
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotation),
                tint = MeTheme.colorScheme.iconPrimary,
            )
        }
        AnimatedVisibility(visible = expanded) {
            GeneralMetricsSection(generalMetrics)
            r4ScaleMetrics?.let { r4Controls ->
                R4ScaleMetricsSection(r4Controls)
            }
        }
        val spacing = if (!expanded) MeTheme.spacing.x3l else MeTheme.spacing.xl
        Spacer(modifier = Modifier.height(spacing))

    }
}

@Preview
@Composable
private fun ExpandableMetricsCardPreview() {
    MeAppTheme {
        val general = GeneralMetricsFormControls(
            bodyMassIndex = FormControl.create("", emptyList()),
            bodyFat = FormControl.create("", emptyList()),
            muscleMass = FormControl.create("", emptyList()),
            bodyWater = FormControl.create("", emptyList()),
        )
        val r4 = R4ScaleMetricsFormControls(
            heartRate = FormControl.create("", emptyList()),
            boneMass = FormControl.create("", emptyList()),
            visceralFat = FormControl.create("", emptyList()),
            subcutaneousFat = FormControl.create("", emptyList()),
            protein = FormControl.create("", emptyList()),
            skeletalMuscles = FormControl.create("", emptyList()),
            bmr = FormControl.create("", emptyList()),
            metabolicAge = FormControl.create("", emptyList()),
        )
        ExpandableMetricsCard(
            title = EntryScreenStrings.METRICS_SECTION_TITLE,
            subheading = EntryScreenStrings.METRICS_SECTION_SUBHEADING,
            generalMetrics = general,
            r4ScaleMetrics = r4,
            expandedInitially = true,
        )
    }
}
