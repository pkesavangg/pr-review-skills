package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
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
        AppText(BabySignupStrings.EmptyDescription, TextType.Title, spacing = MeTheme.spacing.lg)

        Column(
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.x3s),
        ) {
            babies.forEachIndexed { index, baby ->
                AppSwipeableListItem(
                    onActionOpened = { openedIdx -> openIndex = openedIdx },
                    isSwipeable = true,
                    index = index,
                    iconWidth = 56.dp,
                    showAction = openIndex == index,
                    actionContent = {
                        AppSwipeableListActions {
                            AppSwipeableActionItem(
                                iconId = AppIcons.Default.Delete,
                                contentDescription = BabySignupStrings.deleteBaby,
                                backgroundColor = MeTheme.colorScheme.danger,
                            ) {
                                onDeleteBaby(baby)
                            }
                        }
                    },
                ) {
                    BaseListItem(
                        title = baby.name,
                        leadingContent = {
                            BabyAvatar(name = baby.name)
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

@Composable
private fun BabyAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    val initial = name.firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MeTheme.colorScheme.secondaryBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MeTheme.typography.heading5,
            color = MeTheme.colorScheme.textBody,
            textAlign = TextAlign.Center,
        )
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
