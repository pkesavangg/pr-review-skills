package com.greatergoods.meapp.features.addScale.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.addScale.reducer.AddScaleFormControls
import com.greatergoods.meapp.features.addScale.reducer.AddScaleIntent
import com.greatergoods.meapp.features.addScale.reducer.AddScaleState
import com.greatergoods.meapp.features.addScale.strings.AddScaleScreenStrings
import com.greatergoods.meapp.features.addScale.viewmodel.AddScaleViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppScaleCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.ScaleList
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun AddScaleScreen(viewModel: AddScaleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    AddScaleScreenContent(state, viewModel::handleIntent)
}

@Composable
fun AddScaleScreenContent(
    state: AddScaleState,
    handleIntent: (AddScaleIntent) -> Unit
) {
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val modelNumberControl = state.form.controls.modelNumber
    val modelNumberFocusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

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
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { focusManager.clearFocus() },
                ),
        ) {
            Column(modifier = Modifier
                .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md)
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
                        // handle submit
                        focusManager.clearFocus()
                    },
                    showTrailingIcon = true,
                    showTrailingIconAlways = true,
                    trailingIconId = AppIcons.Outlined.Help,
                    onTrailingAction = { handleIntent(AddScaleIntent.ShowHelp) },
                    modifier = Modifier
                        .semantics { contentType = ContentType.PhoneNumber }
                        .focusRequester(modelNumberFocusRequester),
                )

                Spacer(modifier = Modifier.height(MeTheme.spacing.lg))

                AppButton(
                    label = AddScaleScreenStrings.Submit,
                    type = ButtonType.PrimaryFilled,
                    size = ButtonSize.Large,
                    enabled = state.form.isValid,
                    onClick = { handleIntent(AddScaleIntent.Submit) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
                AppButton(
                    label = AddScaleScreenStrings.CantFindModelNumber,
                    type = ButtonType.TextPrimary,
                    onClick = { handleIntent(AddScaleIntent.OpenScaleChooser) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
                AppText(
                    text = AddScaleScreenStrings.MyScales,
                    textType = TextType.Title,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                state.savedScales.forEach { scale ->
                    AppScaleCard(
                        scale = scale,
                        isSavedScale = true,
                        onClick = {},
                    )
                }
            }
        }

    }
}

@PreviewTheme
@Composable
fun AddScaleScreenPreview() {
    MeAppTheme {
        val dummyAddScaleState = AddScaleState(
            form = FormGroup(
                controls = AddScaleFormControls(
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
