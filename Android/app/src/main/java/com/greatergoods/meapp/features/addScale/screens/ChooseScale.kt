package com.greatergoods.meapp.features.addScale.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.addScale.reducer.AddScaleIntent
import com.greatergoods.meapp.features.addScale.strings.ChooseScaleStrings
import com.greatergoods.meapp.features.addScale.viewmodel.AddScaleViewModel
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.ScaleList
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import kotlinx.coroutines.launch

@Composable
fun ChooseScaleScreen(viewModel: AddScaleViewModel = hiltViewModel()) {
    ChooseScaleScreenContent(viewModel::handleIntent)
}

@Composable
fun ChooseScaleScreenContent(
    handleIntent: (AddScaleIntent) -> Unit
) {
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()

    AppScaffold(
        title = ChooseScaleStrings.Header,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                coroutineScope.launch {
                    backStack.removeLast()
                }
            }
        },
    ) {
        Column(modifier = Modifier.padding(vertical = spacing.md)) {
            ScaleList(
                onScaleSelected = { scale ->
                    handleIntent(AddScaleIntent.ScaleSelected(scale.sku))
                },
            )
        }
    }
}

@PreviewTheme
@Composable
fun ChooseScaleScreenPreview() {
    MeAppTheme {
        ChooseScaleScreenContent(
            handleIntent = {},
        )
    }
}
