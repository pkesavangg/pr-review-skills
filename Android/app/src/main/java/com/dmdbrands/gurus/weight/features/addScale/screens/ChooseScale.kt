package com.dmdbrands.gurus.weight.features.addScale.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.core.navigation.LocalProductType
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleIntent
import com.dmdbrands.gurus.weight.features.addScale.strings.ChooseScaleStrings
import com.dmdbrands.gurus.weight.features.addScale.viewmodel.AddScaleViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.ScaleList
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.coroutines.launch

@Composable
fun ChooseScaleScreen(viewModel: AddScaleViewModel = hiltViewModel()) {
  ChooseScaleScreenContent(viewModel::handleIntent)
}

@Composable
fun ChooseScaleScreenContent(handleIntent: (AddScaleIntent) -> Unit = {}) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()

  AppScaffold(
    title = ChooseScaleStrings.header(LocalProductType.current),
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        coroutineScope.launch {
          backStack.removeLast()
        }
      }
    },
  ) {
    Column(
      modifier =
        Modifier
          .padding(vertical = spacing.md),
    ) {
      ScaleList(
        onScaleSelected = { scaleInfo ->
          handleIntent(AddScaleIntent.OpenSelectedScaleSetup(scaleInfo.sku))
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
