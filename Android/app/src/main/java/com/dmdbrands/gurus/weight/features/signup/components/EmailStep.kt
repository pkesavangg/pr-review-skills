package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentType
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
 * Step for collecting user's email address
 */
@Composable
fun EmailStep(
    emailControl: FormControl<String>,
    onNext: () -> Unit = {},
) {
    val emailFocusRequester = remember { FocusRequester() }

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        // TalkBack: the step title is a heading for by-heading navigation.
        AppText(
            SignupStrings.emailStepTitle,
            TextType.Title,
            spacing = MeTheme.spacing.xs,
            modifier = Modifier.semantics { heading() },
        )
        AppText(SignupStrings.emailStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
        AppInput(
            formControl = emailControl,
            type = AppInputType.EMAIL,
            label = SignupStrings.emailLabel,
            imeAction = ImeAction.Next,
            onImeAction = onNext,
            testTag = TestTags.Signup.EmailField,
            modifier = Modifier.semantics { contentType = ContentType.NewUsername }.focusRequester(emailFocusRequester),
        )
    }
}

@PreviewTheme
@Composable
fun EmailStepPreview() {
    MeAppTheme {
        EmailStep(
            emailControl = FormControl.create("", listOf(FormValidations.required(), FormValidations.email())),
            onNext = {},
        )
    }
}
