package com.dmdbrands.gurus.weight.features.addScale.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleFormControls
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleIntent
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleState
import com.dmdbrands.gurus.weight.features.addScale.strings.AddScaleScreenStrings
import com.dmdbrands.gurus.weight.features.addScale.viewmodel.AddScaleViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppScaleCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun AddScaleScreen(viewModel: AddScaleViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  AddScaleScreenContent(state, viewModel::handleIntent)
}

@Composable
fun AddScaleScreenContent(
  state: AddScaleState,
  handleIntent: (AddScaleIntent) -> Unit,
) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val modelNumberControl = state.form.controls.modelNumber
  val modelNumberFocusRequester = remember { FocusRequester() }
  val interactionSource = remember { MutableInteractionSource() }

  DisposableEffect(Unit) {
    onDispose {
      handleIntent(AddScaleIntent.ResetForm)
    }
  }

  AppScaffold(
    title = AddScaleScreenStrings.Header,
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
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { focusManager.clearFocus() },
          ),
    ) {
      Column(
        modifier =
          Modifier
            .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
      ) {
        AppText(
          text = AddScaleScreenStrings.Title,
          textType = TextType.Title,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        AppText(
          text = AddScaleScreenStrings.Subtitle,
          textType = TextType.Body,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
        AppInput(
          formControl = modelNumberControl,
          label = AddScaleScreenStrings.ModelNumberLabel,
          type = AppInputType.NUMERIC_STRING,
          imeAction = ImeAction.Done,
          onImeAction = {
            keyboardController?.hide()
            handleIntent(AddScaleIntent.Submit)
            focusManager.clearFocus()
          },
          showTrailingIcon = true,
          showTrailingIconAlways = true,
          trailingIconId = AppIcons.Outlined.Help,
          onTrailingAction = { handleIntent(AddScaleIntent.ShowHelp) },
          modifier =
            Modifier
              .semantics { contentType = ContentType.PhoneNumber }
              .focusRequester(modelNumberFocusRequester),
          maxLength = 4,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))

        AppButton(
          label = AddScaleScreenStrings.Submit,
          type = ButtonType.PrimaryFilled,
          size = ButtonSize.Large,
          enabled = state.form.isValid && modelNumberControl.value.isNotBlank(),
          onClick = {
            keyboardController?.hide()
            handleIntent(AddScaleIntent.Submit)
          },
          modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        AppButton(
          label = AddScaleScreenStrings.CantFindModelNumber,
          type = ButtonType.TextPrimary,
          size = ButtonSize.XSmall,
          onClick = { handleIntent(AddScaleIntent.OpenScaleChooser) },
          modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
        if (state.savedScales.isNotEmpty()) {
          AppText(
            text = AddScaleScreenStrings.MyScales,
            textType = TextType.Title,
          )
        }
      }
      if (state.savedScales.isNotEmpty()) {
        Column(
          modifier =
            Modifier
              .fillMaxWidth(),
        ) {
          state.savedScales.forEach { scaleInfo ->
            AppScaleCard(
              scale = scaleInfo,
              isSavedScale = true,
              onClick = { selectedScaleInfo ->
                selectedScaleInfo.scaleId?.let { id ->
                  handleIntent(AddScaleIntent.OpenScaleSettings(id))
                }
              },
            )
          }
        }
      }
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
