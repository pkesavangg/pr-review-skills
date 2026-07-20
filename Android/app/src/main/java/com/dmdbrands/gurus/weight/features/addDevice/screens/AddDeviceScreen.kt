package com.dmdbrands.gurus.weight.features.addDevice.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddScaleFormControls
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddDeviceIntent
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddScaleState
import com.dmdbrands.gurus.weight.features.addDevice.strings.AddDeviceScreenStrings
import com.dmdbrands.gurus.weight.features.addDevice.viewmodel.AddDeviceViewModel
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppDeviceCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun AddDeviceScreen(viewModel: AddDeviceViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  // Re-pull paired devices from the server each time this screen is shown, so a scale paired
  // on another phone appears without an app restart. (MOB-1201)
  LaunchedEffect(Unit) {
    viewModel.refreshSavedScales()
  }
  AddScaleScreenContent(state, viewModel::handleIntent)
}

@Composable
fun AddScaleScreenContent(
  state: AddScaleState,
  handleIntent: (AddDeviceIntent) -> Unit,
) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val modelNumberControl = state.form.controls.modelNumber
  val modelNumberFocusRequester = remember { FocusRequester() }

  DisposableEffect(Unit) {
    onDispose {
      handleIntent(AddDeviceIntent.ResetForm)
    }
  }

  AppScaffold(
    title = AddDeviceScreenStrings.Header,
    navigationIcon = {
      // TalkBack: icon-only button needs a spoken label.
      AppIconButton(
        AppIcons.Default.Close,
        contentDescription = AddDeviceScreenStrings.accCloseButton,
      ) {
        coroutineScope.launch {
          backStack.removeLast()
        }
      }
    },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .dismissKeyboardOnTap(),
    ) {
      AddDeviceFormSection(
        state = state,
        modelNumberControl = modelNumberControl,
        modelNumberFocusRequester = modelNumberFocusRequester,
        onSubmit = {
          keyboardController?.hide()
          handleIntent(AddDeviceIntent.Submit)
        },
        onImeSubmit = {
          keyboardController?.hide()
          handleIntent(AddDeviceIntent.Submit)
          focusManager.clearFocus()
        },
        onShowHelp = { handleIntent(AddDeviceIntent.ShowHelp) },
        onOpenScaleChooser = { handleIntent(AddDeviceIntent.OpenScaleChooser) },
      )
      if (state.savedScales.isNotEmpty()) {
        SavedScalesList(
          savedScales = state.savedScales,
          onSelect = { id -> handleIntent(AddDeviceIntent.OpenDeviceSettings(id)) },
        )
      }
    }
  }
}

@Composable
private fun AddDeviceFormSection(
  state: AddScaleState,
  modelNumberControl: FormControl<String>,
  modelNumberFocusRequester: FocusRequester,
  onSubmit: () -> Unit,
  onImeSubmit: () -> Unit,
  onShowHelp: () -> Unit,
  onOpenScaleChooser: () -> Unit,
) {
  Column(
    modifier = Modifier.padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
  ) {
    AppText(
      text = AddDeviceScreenStrings.Title,
      textType = TextType.Title,
      // TalkBack: screen title is the heading.
      modifier = Modifier.semantics { heading() },
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
    AppText(
      text = AddDeviceScreenStrings.Subtitle,
      textType = TextType.Body,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
    AppInput(
      formControl = modelNumberControl,
      label = AddDeviceScreenStrings.ModelNumberLabel,
      type = AppInputType.NUMERIC_STRING,
      imeAction = ImeAction.Done,
      onImeAction = onImeSubmit,
      showTrailingIcon = true,
      showTrailingIconAlways = true,
      trailingIconId = AppIcons.Outlined.Help,
      onTrailingAction = onShowHelp,
      modifier = Modifier
        .semantics { contentType = ContentType.PhoneNumber }
        .focusRequester(modelNumberFocusRequester),
      maxLength = 4,
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.lg))

    AppButton(
      label = AddDeviceScreenStrings.Submit,
      type = ButtonType.PrimaryFilled,
      size = ButtonSize.Large,
      enabled = state.form.isValid && modelNumberControl.value.isNotBlank(),
      onClick = onSubmit,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
    AppButton(
      label = AddDeviceScreenStrings.CantFindModelNumber,
      type = ButtonType.TextPrimary,
      size = ButtonSize.XSmall,
      onClick = onOpenScaleChooser,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
    if (state.savedScales.isNotEmpty()) {
      AppText(
        text = AddDeviceScreenStrings.MyDevices,
        textType = TextType.Title,
        // TalkBack: section header is a heading.
        modifier = Modifier.semantics { heading() },
      )
    }
  }
}

@Composable
private fun SavedScalesList(
  savedScales: List<DeviceModelInfo>,
  onSelect: (String) -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth(),
  ) {
    savedScales.forEach { scaleInfo ->
      AppDeviceCard(
        scale = scaleInfo,
        isSavedScale = true,
        onClick = { selectedScaleInfo ->
          selectedScaleInfo.scaleId?.let { id ->
            onSelect(id)
          }
        },
      )
    }
  }
}

@PreviewTheme
@Composable
fun AddScaleScreenPreview() {
  MeAppTheme {
    val dummyAddScaleState =
      AddScaleState(
        form =
          FormGroup(
            controls =
              AddScaleFormControls(
                modelNumber = FormControl.create(""),
              ),
          ),
        isSubmitting = false,
      )
    AddScaleScreenContent(
      state = dummyAddScaleState,
      handleIntent = {},
    )
  }
}
