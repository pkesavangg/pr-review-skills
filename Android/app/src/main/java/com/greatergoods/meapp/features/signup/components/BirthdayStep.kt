package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.core.shared.utilities.DateTimeTools
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.DateTimeInput
import com.greatergoods.meapp.features.common.components.DateTimeInputMode
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Step for collecting user's birthday
 */
@Composable
fun BirthdayStep(birthdayControl: FormControl<DateTimeValue>) {
    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
    ) {
        AppText(SignupStrings.birthdayStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(SignupStrings.birthdayStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.md)
        DateTimeInput(
            formControl = birthdayControl,
            mode = DateTimeInputMode.Date,
            maxValue = DateTimeValue.Date(DateTimeTools.getMinBirthdayOffsetForDatePicker()), // 120 years ago (oldest)
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
    }
}

@PreviewTheme
@Composable
fun BirthdayStepPreview() {
    MeAppTheme {
        BirthdayStep(
            birthdayControl =
                FormControl.create(
                    DateTimeValue.Date(System.currentTimeMillis()),
                    listOf(),
                ),
        )
    }
}
