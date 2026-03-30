package com.dmdbrands.gurus.weight.features.manualEntry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.ProductTypeHeader
import com.dmdbrands.gurus.weight.features.manualEntry.components.BabyEntrySection
import com.dmdbrands.gurus.weight.features.manualEntry.components.BloodPressureSection
import com.dmdbrands.gurus.weight.features.manualEntry.components.WeightEntrySection
import com.dmdbrands.gurus.weight.features.manualEntry.components.ExpandableMetricsCard
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.ActiveEntryForm
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.EntryIntent
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.EntryState
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.EntryViewModel
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun EntryScreen() {
  val viewModel: EntryViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.observeProductSelection()
  }

  EntryScreenContent(
    state = state,
    productSelectionManager = viewModel.productSelectionManager,
    initializeDeactivate = viewModel::initDeactivate,
    handleIntent = viewModel::handleIntent,
  )
}

@Composable
private fun EntryScreenContent(
  state: EntryState,
  productSelectionManager: IProductSelectionManager,
  initializeDeactivate: (() -> Unit) -> Unit,
  handleIntent: (EntryIntent) -> Unit,
) {
  val selectedProduct by productSelectionManager.selectedProduct
    .collectAsStateWithLifecycle()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) {
    handleIntent(EntryIntent.UpdateOnRelaunch)
    initializeDeactivate {
      focusManager.clearFocus()
      keyboardController?.hide()
    }
  }

  val navStackController = LocalNavBackStack.current
  val scope = rememberCoroutineScope()
  BackHandler {
    scope.launch {
      navStackController.removeLast(AppRoute.Home)
    }
  }

  val scrollState = rememberScrollState()
  val interactionSource = remember { MutableInteractionSource() }

  AppScaffold(
    title = null,
    topBarContent = {
      ProductTypeHeader(
        selectedProduct = selectedProduct,
        onClick = { productSelectionManager.showProductSheet(EntryScreenStrings.Title) },
      )
    },
  ) {
    Column(
      modifier = Modifier
        .verticalScroll(scrollState)
        .padding(horizontal = MeTheme.spacing.sm)
        .padding(top = MeTheme.spacing.md)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = { focusManager.clearFocus() },
        ),
      verticalArrangement = Arrangement.Top,
    ) {
      when (selectedProduct) {
        is ProductSelection.MyWeight -> WeightEntrySection(state = state)

        is ProductSelection.BloodPressure -> {
          val bpForm = (state.activeForm as? ActiveEntryForm.BloodPressure)?.form
          if (bpForm != null) {
            BloodPressureSection(
              controls = bpForm.forms.bloodPressure.controls,
              onImeAction = {
                focusManager.clearFocus()
                keyboardController?.hide()
              },
            )
          }
        }

        is ProductSelection.Baby -> {
          val babyForm = (state.activeForm as? ActiveEntryForm.Baby)?.form
          if (babyForm != null) {
            BabyEntrySection(
              controls = babyForm.forms.baby.controls,
              onImeAction = {
                focusManager.clearFocus()
                keyboardController?.hide()
              },
            )
          }
        }
      }

      // Single save button for all product types
      Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AppButton(
          enabled = state.activeForm.isValid,
          label = EntryScreenStrings.SaveButton,
          size = ButtonSize.Large,
          type = ButtonType.PrimaryFilled,
          onClick = {
            keyboardController?.hide()
            handleIntent(EntryIntent.Save)
          },
        )
      }
      Spacer(modifier = Modifier.height(MeTheme.spacing.x3l))
    }
  }
}

