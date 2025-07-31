package com.dmdbrands.gurus.weight.features.weightless.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.changePassword.strings.ChangePasswordStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.AppToggle
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessFormControls
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessIntent
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessState
import com.dmdbrands.gurus.weight.features.weightless.strings.WeightlessStrings
import com.dmdbrands.gurus.weight.features.weightless.viewmodel.WeightlessViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Weightless screen composable. Displays the weightless form, handles user input, and shows loading/error states.
 */
@Composable
fun WeightlessScreen() {
    val viewmodel: WeightlessViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()
    BackHandler {
        viewmodel.handleIntent(WeightlessIntent.OnBack)
    }
    WeightlessContent(state, viewmodel::handleIntent)
}

@Composable
private fun WeightlessContent(state: WeightlessState, handleIntent: (WeightlessIntent) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val weightFocusRequester = remember { FocusRequester() }

    // Get weight unit from state
    val weightUnit = state.weightUnit

    AppScaffold(
        title = WeightlessStrings.PageTitle,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) { handleIntent(WeightlessIntent.OnBack) }
        },
        actions = {
            AppButton(
                ChangePasswordStrings.SaveButton,
                type = ButtonType.InlineTextPrimary,
                size = ButtonSize.Small,
                enabled = (state.form.isValid && state.form.isDirty) || state.hasToggleChanged,
            ) {
                keyboardController?.hide()
                handleIntent.invoke(WeightlessIntent.Submit)
            }
        },
        containerColor = colorScheme.secondaryBackground,
        appBarColor = colorScheme.secondaryBackground,
        borderColor = Color.Transparent,
    ) { scaffoldModifier ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            AppStyledCard {
                Spacer(Modifier.height(spacing.md))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { focusManager.clearFocus() },
                            ),
                    horizontalAlignment = Alignment.Start,
                ) {
                    AppText(
                        text = WeightlessStrings.Title,
                        textType = TextType.Title,
                    )

                    Spacer(Modifier.height(spacing.sm))
                    AppText(
                        text = WeightlessStrings.Description,
                        textType = TextType.Subtitle,
                    )
                    Spacer(Modifier.height(spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val toggleStatus = if (state.isWeightlessOn) {
                            WeightlessStrings.WeightlessOn
                        } else {
                            WeightlessStrings.WeightlessOff
                        }
                        AppText(
                            text = "${WeightlessStrings.WeightlessToggleLabel} $toggleStatus",
                            textType = TextType.Body,
                        )
                        AppToggle(
                            checked = state.isWeightlessOn,
                            onCheckedChange = {
                                handleIntent(WeightlessIntent.ToggleWeightless)
                            },
                        )
                    }

                    Spacer(Modifier.height(spacing.md))

                    // Weight Input (only shown when weightless mode is enabled)
                        AppInput(
                            formControl = state.form.controls.weightlessWeight,
                            enabled = state.isWeightlessOn,
                            label = "${WeightlessStrings.WeightlessWeightLabel} ($weightUnit)",
                            placeHolder = "Enter weight in $weightUnit",
                            type = AppInputType.BODY_COMP,
                            showTrailingIcon = false,
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                handleIntent(WeightlessIntent.Submit)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.focusRequester(weightFocusRequester),
                        )
                    Spacer(Modifier.height(spacing.lg))
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun WeightlessScreenPreview() {
    MeAppTheme {
        val dummyWeightlessState = WeightlessState(
            form = FormGroup(
                controls = WeightlessFormControls(
                    weightlessWeight = FormControl.create(
                        initialValue = "150.0",
                        validators = emptyList(),
                    ),
                ),
            ),
            isWeightlessOn = true,
            hasToggleChanged = false,
        )
        WeightlessContent(dummyWeightlessState) {}
    }
}
