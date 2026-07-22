package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.GeneralMetricsFormControls
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.R4ScaleMetricsFormControls
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Expandable card for metrics section, with animated expand/collapse, title, and optional subheading.
 *
 * @param title The title of the section.
 * @param subheading Optional subheading below the title.
 * @param generalMetrics The form controls for general metrics.
 * @param r4ScaleMetrics The form controls for R4/scale metrics (optional).
 * @param expandedInitially Whether the card is expanded by default.
 * @param onImeAction Optional callback for when the last input's IME action is triggered.
 * @param dashboardType The dashboard type to determine the focus flow.
 */
@Composable
fun ExpandableMetricsCard(
    title: String,
    subheading: String? = null,
    generalMetrics: GeneralMetricsFormControls,
    r4ScaleMetrics: R4ScaleMetricsFormControls? = null,
    expandedInitially: Boolean = false,
    onImeAction: (() -> Unit)? = null,
    dashboardType: DashboardType = DashboardType.DASHBOARD_12_METRICS,
    enabled: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(expandedInitially) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "ChevronRotation")
    // Create focus requester for heart rate (first field of R4ScaleMetricsSection)
    val heartRateFocusRequester = remember { FocusRequester() }

    Column {
        MetricsCardHeader(
            title = title,
            subheading = subheading,
            expanded = expanded,
            rotation = rotation,
            onToggle = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            MetricsCardContent(
                generalMetrics = generalMetrics,
                r4ScaleMetrics = r4ScaleMetrics,
                dashboardType = dashboardType,
                heartRateFocusRequester = heartRateFocusRequester,
                onImeAction = onImeAction,
                enabled = enabled,
            )
        }
        val spacing = if (!expanded) MeTheme.spacing.x3l else MeTheme.spacing.xl
        Spacer(modifier = Modifier.height(spacing))
    }
}

/** Clickable title/subheading row with the animated chevron; toggles the card via [onToggle]. */
@Composable
private fun MetricsCardHeader(
    title: String,
    subheading: String?,
    expanded: Boolean,
    rotation: Float,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  // TalkBack: this Row is the actual toggle control — announce it as a button
                  // and source the expand/collapse action label from strings. The chevron icon
                  // below is decorative (contentDescription = null) so the action isn't read twice.
                  role = Role.Button,
                  onClickLabel =
                      if (expanded) {
                          EntryScreenStrings.accMetricsCollapseLabel
                      } else {
                          EntryScreenStrings.accMetricsExpandLabel
                      },
                ) { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MeTheme.typography.heading4,
                color = MeTheme.colorScheme.textHeading,
                // TalkBack: mark the metrics section title as a heading so users
                // can navigate to it by heading. The Text's own text is the spoken name.
                modifier = Modifier.semantics { heading() },
            )
            if (subheading != null) {
                Text(
                    subheading,
                    style = MeTheme.typography.subHeading1,
                    color = MeTheme.colorScheme.textSubheading,
                )
            }
        }
        Icon(
            painter = painterResource(AppIcons.Default.ChevronDown),
            // Decorative: the expand/collapse action is announced on the clickable Row above
            // (role = Button + onClickLabel), so the chevron must not repeat it.
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
            tint = MeTheme.colorScheme.iconPrimary,
        )
    }
}

/** Expanded body: general metrics, plus R4/scale metrics when the dashboard type includes them. */
@Composable
private fun MetricsCardContent(
    generalMetrics: GeneralMetricsFormControls,
    r4ScaleMetrics: R4ScaleMetricsFormControls?,
    dashboardType: DashboardType,
    heartRateFocusRequester: FocusRequester,
    onImeAction: (() -> Unit)?,
    enabled: Boolean,
) {
    Column {
        GeneralMetricsSection(
            generalMetrics,
            dashboardType,
            nextFocusRequester =
                if (dashboardType != DashboardType.DASHBOARD_4_METRICS &&
                    r4ScaleMetrics != null
                ) {
                    heartRateFocusRequester
                } else {
                    null
                },
            onImeAction = if (dashboardType == DashboardType.DASHBOARD_4_METRICS) onImeAction else null,
            enabled = enabled,
        )
        if (dashboardType == DashboardType.DASHBOARD_12_METRICS && r4ScaleMetrics != null) {
            R4ScaleMetricsSection(
                r4ScaleMetrics,
                onImeAction = onImeAction,
                heartRateFocusRequester = heartRateFocusRequester,
                enabled = enabled,
            )
        }
    }
}

@Preview
@Composable
private fun ExpandableMetricsCardPreview() {
    MeAppTheme {
        val general =
            GeneralMetricsFormControls(
                bodyMassIndex = FormControl.create("", emptyList()),
                bodyFat = FormControl.create("", emptyList()),
                muscleMass = FormControl.create("", emptyList()),
                bodyWater = FormControl.create("", emptyList()),
            )
        val r4 =
            R4ScaleMetricsFormControls(
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
            dashboardType = DashboardType.DASHBOARD_4_METRICS,
            expandedInitially = true,
        )
    }
}
