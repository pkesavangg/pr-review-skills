package com.dmdbrands.gurus.weight.features.myKids.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.features.common.components.AppProfileAvatar
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableActionItem
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListActions
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListItem
import com.dmdbrands.gurus.weight.features.common.components.BaseListItem
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.myKids.strings.MyKidsStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private const val CHEVRON_EXPANDED_ROTATION = 180f
private val DIVIDER_THICKNESS = 0.5.dp
private val ICON_SIZE = 20.dp
private val SWIPE_ACTION_WIDTH = 56.dp

/**
 * Settings → My Kids list: one expandable card row per baby (Figma node 34059-32734).
 *
 * Collapsed a row shows the baby's avatar initial + name with an edit pencil and an
 * expand chevron; expanded it reveals Birthday, Biological sex, Birth length and Birth
 * weight (formatted for the account's [measurementUnits] via [babyDetailValues], which reuses
 * the shared baby ConversionTools helpers). Tapping the edit pencil opens the
 * edit-baby flow; tapping the row or chevron toggles the details; swiping a row reveals
 * delete (same primitive + position-aware corners as
 * [com.dmdbrands.gurus.weight.features.common.components.KidsList], which the signup
 * Add-Baby step keeps as a flat list with no per-baby details).
 *
 * Expansion state is local, so all rows start collapsed each time the screen opens.
 * Callers own the surrounding title and the "Add a baby" button.
 */
@Composable
fun ExpandableBabyList(
    babies: ImmutableList<BabyProfile>,
    measurementUnits: MeasurementUnits,
    onEditBaby: (String) -> Unit,
    onDeleteBaby: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track the single swipe-opened row and the single expanded row independently.
    var openIndex by remember { mutableStateOf<Int?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val isMetric = measurementUnits == MeasurementUnits.METRIC

    Column(modifier = modifier.fillMaxWidth()) {
        babies.forEachIndexed { index, baby ->
            val displayName = baby.name.ifEmpty { "${MyKidsStrings.BabyFallbackPrefix} ${index + 1}" }
            BabyRow(
                baby = baby,
                displayName = displayName,
                index = index,
                count = babies.size,
                isMetric = isMetric,
                isExpanded = expandedId == baby.id,
                isSwipeOpen = openIndex == index,
                onSwipeOpened = { openedIdx -> openIndex = openedIdx },
                onToggle = { expandedId = if (expandedId == baby.id) null else baby.id },
                onEditBaby = onEditBaby,
                onDeleteBaby = onDeleteBaby,
            )
        }
    }
}

/** A single swipeable baby row: header (avatar + name + edit + chevron) plus expandable details. */
@Composable
private fun BabyRow(
    baby: BabyProfile,
    displayName: String,
    index: Int,
    count: Int,
    isMetric: Boolean,
    isExpanded: Boolean,
    isSwipeOpen: Boolean,
    onSwipeOpened: (Int?) -> Unit,
    onToggle: () -> Unit,
    onEditBaby: (String) -> Unit,
    onDeleteBaby: (String) -> Unit,
) {
    val corner = MeTheme.borderRadius.sm
    val lastIndex = count - 1
    // Round the revealed delete action's outer corners to match the list card
    // (first row -> top, last row -> bottom), mirroring KidsList / AppUserList.
    val actionShape = when {
        count == 1 -> RoundedCornerShape(topEnd = corner + 2.dp, bottomEnd = corner + 2.dp)
        index == 0 -> RoundedCornerShape(topEnd = corner + 2.dp)
        index == lastIndex -> RoundedCornerShape(bottomEnd = corner + 2.dp)
        else -> RectangleShape
    }
    AppSwipeableListItem(
        onActionOpened = onSwipeOpened,
        isSwipeable = true,
        index = index,
        iconWidth = SWIPE_ACTION_WIDTH,
        showAction = isSwipeOpen,
        actionContent = {
            BabyRowDeleteAction(
                shape = actionShape,
                // Guard: only delete when the row is actually swiped open, so a tap that lands on the
                // trailing edge (over the collapsed action strip, e.g. the chevron) can never delete.
                onDelete = { if (isSwipeOpen) onDeleteBaby(baby.id) },
            )
        },
    ) { progress ->
        // While dragging, square the corners so the row meets the red action cleanly;
        // at rest, round the card's outer corners by row position (mirrors KidsList).
        val r = if (progress > 0f) 0.dp else corner
        val rowShape = when {
            count == 1 -> RoundedCornerShape(r)
            index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
            index == lastIndex -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
            else -> RectangleShape
        }
        BabyRowContent(
            baby = baby,
            displayName = displayName,
            isMetric = isMetric,
            isExpanded = isExpanded,
            showDivider = index < lastIndex,
            rowShape = rowShape,
            onToggle = onToggle,
            onEditBaby = onEditBaby,
        )
    }
}

/** The at-rest row content (header + expandable details + divider) clipped to [rowShape]. */
@Composable
private fun BabyRowContent(
    baby: BabyProfile,
    displayName: String,
    isMetric: Boolean,
    isExpanded: Boolean,
    showDivider: Boolean,
    rowShape: Shape,
    onToggle: () -> Unit,
    onEditBaby: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            // Fill width so the row content fully covers the swipe-delete action strip behind it;
            // otherwise a tap on the trailing edge can fall through to the hidden delete action.
            .fillMaxWidth()
            .clip(rowShape)
            .background(MeTheme.colorScheme.primaryBackground, rowShape)
            .testTag(TestTags.MyKids.BabyRow),
    ) {
        BaseListItem(
            title = displayName,
            leadingContent = { AppProfileAvatar(text = displayName, isActive = false) },
            onClick = onToggle,
            trailingContent = {
                BabyRowActions(isExpanded = isExpanded, onEdit = { onEditBaby(baby.id) }, onToggle = onToggle)
            },
        )
        AnimatedVisibility(visible = isExpanded) {
            BabyDetails(baby = baby, isMetric = isMetric)
        }
        if (showDivider) {
            HorizontalDivider(color = MeTheme.colorScheme.utility, thickness = DIVIDER_THICKNESS)
        }
    }
}

/** The red swipe-revealed delete action for a baby row. */
@Composable
private fun RowScope.BabyRowDeleteAction(shape: Shape, onDelete: () -> Unit) {
    AppSwipeableListActions(shape = shape) {
        AppSwipeableActionItem(
            iconId = AppIcons.Default.Delete,
            contentDescription = MyKidsStrings.DeleteBaby,
            backgroundColor = MeTheme.colorScheme.danger,
            onClick = onDelete,
        )
    }
}

/** Trailing edit-pencil + expand-chevron controls for a baby row. */
@Composable
private fun BabyRowActions(isExpanded: Boolean, onEdit: () -> Unit, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEdit, modifier = Modifier.testTag(TestTags.MyKids.EditBabyButton)) {
            Icon(
                painter = painterResource(AppIcons.Default.EditPencil),
                contentDescription = MyKidsStrings.EditBaby,
                tint = MeTheme.colorScheme.textBody,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        IconButton(onClick = onToggle, modifier = Modifier.testTag(TestTags.MyKids.ExpandBabyButton)) {
            Icon(
                painter = painterResource(AppIcons.Default.ChevronDown),
                contentDescription = if (isExpanded) MyKidsStrings.accCollapseLabel else MyKidsStrings.accExpandLabel,
                tint = MeTheme.colorScheme.textBody,
                modifier = Modifier
                    .size(ICON_SIZE)
                    .rotate(if (isExpanded) CHEVRON_EXPANDED_ROTATION else 0f),
            )
        }
    }
}

/** The four profile detail rows revealed when a baby row is expanded. */
@Composable
private fun BabyDetails(baby: BabyProfile, isMetric: Boolean) {
    val details = babyDetailValues(baby, isMetric)
    Column(modifier = Modifier.testTag(TestTags.MyKids.BabyDetails)) {
        DetailRow(label = MyKidsStrings.Birthday, value = details.birthday, showDivider = true)
        DetailRow(label = MyKidsStrings.BiologicalSex, value = details.biologicalSex, showDivider = true)
        DetailRow(label = MyKidsStrings.BirthLength, value = details.birthLength, showDivider = true)
        DetailRow(label = MyKidsStrings.BirthWeight, value = details.birthWeight, showDivider = false)
    }
}

/** A single label (start) / value (end) detail row, with an optional trailing divider. */
@Composable
private fun DetailRow(label: String, value: String, showDivider: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textSubheading,
        )
        Spacer(modifier = Modifier.width(MeTheme.spacing.sm))
        Text(
            text = value,
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textBody,
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
            color = MeTheme.colorScheme.utility,
            thickness = DIVIDER_THICKNESS,
        )
    }
}

@PreviewTheme
@Composable
fun ExpandableBabyListPreview() {
    MeAppTheme {
        ExpandableBabyList(
            babies = persistentListOf(
                BabyProfile(
                    id = "1",
                    accountId = "a",
                    name = "Tammy Thompson",
                    birthdate = "2024-06-10",
                    sex = "male",
                    birthLengthMillimeters = 655,
                    birthWeightDecigrams = 7370,
                ),
                BabyProfile(id = "2", accountId = "a", name = "Sally Thompson"),
            ),
            measurementUnits = MeasurementUnits.IMPERIAL_LB_OZ,
            onEditBaby = {},
            onDeleteBaby = {},
            modifier = Modifier.padding(MeTheme.spacing.sm),
        )
    }
}
