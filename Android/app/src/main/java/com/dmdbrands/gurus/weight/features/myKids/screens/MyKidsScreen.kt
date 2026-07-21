package com.dmdbrands.gurus.weight.features.myKids.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.myKids.components.ExpandableBabyList
import com.dmdbrands.gurus.weight.features.myKids.strings.MyKidsStrings
import com.dmdbrands.gurus.weight.features.myKids.viewmodel.MyKidsIntent
import com.dmdbrands.gurus.weight.features.myKids.viewmodel.MyKidsViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun MyKidsScreen(viewModel: MyKidsViewModel = hiltViewModel()) {
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScaffold(
        title = MyKidsStrings.Title,
        navigationIcon = {
            AppIconButton(
                AppIcons.Default.Close,
                contentDescription = MyKidsStrings.accCloseLabel,
                modifier = Modifier.testTag(TestTags.MyKids.CloseButton),
            ) {
                coroutineScope.launch { backStack.removeLast() }
            }
        },
    ) { scaffoldModifier ->
        if (state.babies.isEmpty()) {
            MyKidsEmptyState(
                modifier = scaffoldModifier,
                onAddBaby = { coroutineScope.launch { backStack.addRoute(AppRoute.AccountSettings.AddBaby()) } },
            )
        } else {
            MyKidsList(
                modifier = scaffoldModifier,
                babies = state.babies,
                measurementUnits = state.measurementUnits,
                onAddBaby = { coroutineScope.launch { backStack.addRoute(AppRoute.AccountSettings.AddBaby()) } },
                onEditBaby = { id -> coroutineScope.launch { backStack.addRoute(AppRoute.AccountSettings.AddBaby(id)) } },
                onDeleteBaby = { viewModel.handleIntent(MyKidsIntent.DeleteBaby(it)) },
            )
        }
    }
}

@Composable
private fun MyKidsList(
    babies: ImmutableList<BabyProfile>,
    measurementUnits: MeasurementUnits,
    onAddBaby: () -> Unit,
    onEditBaby: (String) -> Unit,
    onDeleteBaby: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md)
            .testTag(TestTags.MyKids.ScreenRoot),
    ) {
        AppText(
            text = MyKidsStrings.EmptyDescription,
            textType = TextType.Body,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))

        ExpandableBabyList(
            babies = babies,
            measurementUnits = measurementUnits,
            onEditBaby = onEditBaby,
            onDeleteBaby = onDeleteBaby,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AppButton(
                label = MyKidsStrings.AddABaby,
                modifier = Modifier.testTag(TestTags.MyKids.AddBabyButton),
                type = ButtonType.PrimaryFilled,
                size = ButtonSize.Small,
                onClick = onAddBaby,
            )
        }
    }
}

@Composable
private fun MyKidsEmptyState(
    onAddBaby: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md)
            .testTag(TestTags.MyKids.ScreenRoot),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeTheme.spacing.md),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppText(
                    text = MyKidsStrings.EmptyDescription,
                    textType = TextType.Body,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(MeTheme.spacing.md))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AppButton(
                        label = MyKidsStrings.AddABaby,
                        modifier = Modifier.testTag(TestTags.MyKids.AddBabyButton),
                        type = ButtonType.PrimaryFilled,
                        onClick = onAddBaby,
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun MyKidsScreenPreview() {
    MeAppTheme {
        MyKidsList(
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
            onAddBaby = {},
            onEditBaby = {},
            onDeleteBaby = {},
        )
    }
}
