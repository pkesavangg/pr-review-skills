package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

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
        // TalkBack: the step title is a heading for by-heading navigation.
        AppText(
            SignupStrings.nameStepTitle,
            TextType.Title,
            spacing = MeTheme.spacing.xs,
            modifier = Modifier.semantics { heading() },
        )
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
            imeAction = ImeAction.Next,
            onImeAction = onNext,
            modifier =
                Modifier
                    .focusRequester(lastNameFocusRequester),
        )
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
