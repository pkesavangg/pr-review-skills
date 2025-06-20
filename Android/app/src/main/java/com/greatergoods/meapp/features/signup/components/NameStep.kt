package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for collecting user's first and last name
 */
@Composable
fun NameStep(
    firstNameControl: FormControl<String>,
    lastNameControl: FormControl<String>,
    onNext: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val firstNameFocusRequester = remember { FocusRequester() }
    val lastNameFocusRequester = remember { FocusRequester() }

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        AppText(SignupStrings.nameStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(SignupStrings.nameStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
        AppInput(
            formControl = firstNameControl,
            type = AppInputType.TEXT,
            label = SignupStrings.firstNameLabel,
            imeAction = ImeAction.Next,
            nextFocusRequester = lastNameFocusRequester,
            modifier =
                Modifier
                    .focusRequester(firstNameFocusRequester),
        )
        AppInput(
            formControl = lastNameControl,
            type = AppInputType.TEXT,
            label = SignupStrings.lastNameLabel,
            imeAction = ImeAction.Done,
            onImeAction = onNext,
            modifier =
                Modifier
                    .focusRequester(lastNameFocusRequester),
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
    }
}

@PreviewTheme
@Composable
fun NameStepPreview() {
    MeAppTheme {
        NameStep(
            firstNameControl = FormControl.create("", listOf(FormValidations.required())),
            lastNameControl = FormControl.create("", listOf(FormValidations.required())),
            onNext = {},
        )
    }
}
