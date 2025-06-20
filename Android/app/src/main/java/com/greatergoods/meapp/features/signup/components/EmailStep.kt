package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
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
 * Step for collecting user's email address
 */
@Composable
fun EmailStep(emailControl: FormControl<String>) {
    val emailFocusRequester = remember { FocusRequester() }

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        AppText(SignupStrings.emailStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(SignupStrings.emailStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
        AppInput(
            formControl = emailControl,
            type = AppInputType.EMAIL,
            label = SignupStrings.emailLabel,
            imeAction = ImeAction.Done,
            modifier = Modifier.semantics { contentType = ContentType.NewUsername }.focusRequester(emailFocusRequester),
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
    }
}

@PreviewTheme
@Composable
fun EmailStepPreview() {
    MeAppTheme {
        EmailStep(
            emailControl = FormControl.create("", listOf(FormValidations.required(), FormValidations.email())),
        )
    }
}
