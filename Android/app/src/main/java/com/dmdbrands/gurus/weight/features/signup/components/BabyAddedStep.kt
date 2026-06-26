package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppProfileAvatar
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableActionItem
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListActions
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListItem
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.BaseListItem
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.signup.model.BabyProfile
import com.dmdbrands.gurus.weight.features.signup.strings.BabySignupStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Confirmation step shown after a baby has been added.
 * Displays a list of added babies with edit (tap) and delete (swipe) actions.
 */
@Composable
fun BabyAddedStep(
    babies: List<BabyProfile>,
    onEditBaby: (BabyProfile) -> Unit,
    onDeleteBaby: (BabyProfile) -> Unit,
    onAddBaby: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openIndex by remember { mutableStateOf<Int?>(null) }

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
        modifier = modifier,
    ) {
        // TalkBack: the step title is a heading for by-heading navigation.
        AppText(
            BabySignupStrings.babyAddedTitle,
            TextType.Title,
            spacing = MeTheme.spacing.lg,
            modifier = Modifier.semantics { heading() },
        )

        // Reuses the same swipe primitive + AppProfileAvatar as the switch-account list (AppUserList)
        // for visual parity, with thin dividers between rows. (Figma 31880-34959)
        Column {
            babies.forEachIndexed { index, baby ->
                val corner = MeTheme.borderRadius.sm
                // Round the revealed delete action's outer corners to match the list card
                // (first row → top, last row → bottom), like AppUserList.
                val actionShape = when {
                    babies.size == 1 -> RoundedCornerShape(topEnd = corner + 2.dp, bottomEnd = corner + 2.dp)
                    index == 0 -> RoundedCornerShape(topEnd = corner + 2.dp)
                    index == babies.lastIndex -> RoundedCornerShape(bottomEnd = corner + 2.dp)
                    else -> RectangleShape
                }
                AppSwipeableListItem(
                    onActionOpened = { openedIdx -> openIndex = openedIdx },
                    isSwipeable = true,
                    index = index,
                    iconWidth = 56.dp,
                    showAction = openIndex == index,
                    actionContent = {
                        AppSwipeableListActions(shape = actionShape) {
                            AppSwipeableActionItem(
                                iconId = AppIcons.Default.Delete,
                                contentDescription = BabySignupStrings.deleteBaby,
                                backgroundColor = MeTheme.colorScheme.danger,
                            ) {
                                onDeleteBaby(baby)
                            }
                        }
                    },
                ) { progress ->
                    // While dragging, square the corners so the row meets the red action cleanly;
                    // at rest, round the card's outer corners by row position (mirrors AppUserList).
                    val r = if (progress > 0f) 0.dp else corner
                    val rowShape = when {
                        babies.size == 1 -> RoundedCornerShape(r)
                        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
                        index == babies.lastIndex -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
                        else -> RectangleShape
                    }
                    Column(
                        modifier = Modifier
                            .clip(rowShape)
                            .background(MeTheme.colorScheme.primaryBackground, rowShape),
                    ) {
                        BaseListItem(
                            title = baby.name,
                            leadingContent = {
                                AppProfileAvatar(text = baby.name, isActive = false)
                            },
                            trailingContent = {
                                IconButton(onClick = { onEditBaby(baby) }) {
                                    Icon(
                                        painter = painterResource(AppIcons.Default.EditPencil),
                                        contentDescription = BabySignupStrings.editBaby,
                                        tint = MeTheme.colorScheme.textBody,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                        if (index < babies.lastIndex) {
                            HorizontalDivider(
                                color = MeTheme.colorScheme.utility,
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AppButton(
                label = BabySignupStrings.addABabyButton,
                onClick = onAddBaby,
                type = ButtonType.PrimaryFilled,
                size = ButtonSize.Small,
            )
        }
    }
}

// region: Preview

@PreviewTheme
@Composable
fun BabyAddedStepPreview() {
    MeAppTheme {
        BabyAddedStep(
            babies = listOf(
                BabyProfile(name = "Tammy Thompson"),
                BabyProfile(name = "Sally Thompson"),
            ),
            onEditBaby = {},
            onDeleteBaby = {},
            onAddBaby = {},
        )
    }
}

@PreviewTheme
@Composable
fun BabyAddedStepEmptyPreview() {
    MeAppTheme {
        BabyAddedStep(
            babies = emptyList(),
            onEditBaby = {},
            onDeleteBaby = {},
            onAddBaby = {},
        )
    }
}

// endregion
