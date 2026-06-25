package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.KidListItem
import com.dmdbrands.gurus.weight.features.common.components.KidsList
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.signup.model.BabyProfile
import com.dmdbrands.gurus.weight.features.signup.strings.BabySignupStrings
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
    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
        modifier = modifier,
    ) {
        AppText(BabySignupStrings.babyAddedTitle, TextType.Title, spacing = MeTheme.spacing.lg)

        KidsList(
            kids = babies.map { KidListItem(id = it.id, name = it.name) },
            editContentDescription = BabySignupStrings.editBaby,
            deleteContentDescription = BabySignupStrings.deleteBaby,
            onEditKid = { id -> babies.firstOrNull { it.id == id }?.let(onEditBaby) },
            onDeleteKid = { id -> babies.firstOrNull { it.id == id }?.let(onDeleteBaby) },
        )

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
