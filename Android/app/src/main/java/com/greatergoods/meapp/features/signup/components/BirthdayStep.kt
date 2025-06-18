package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.DateTimeInput
import com.greatergoods.meapp.features.common.components.DateTimeInputMode
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.signup.model.SignupData
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Step for collecting user's date of birth
 */
@Composable
fun BirthdayStep(
    signupData: SignupData,
    onBirthdayChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        AppStyledCard(
            cardAlignmentType = LocalCardAlignment.current
        ) {
            AppText(SignupStrings.birthdayStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
            AppText(SignupStrings.birthdayStepSubtitle, TextType.Subtitle,spacing = MeTheme.spacing.md)
            DateTimeInput(
                value = signupData.birthday?.let { DateTimeValue.Date(it) },
                onValueChange = { if (it is DateTimeValue.Date) onBirthdayChange(it.millis) },
                mode = DateTimeInputMode.Date
            )
            Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.sm))
        }

    }

@PreviewTheme
@Composable
fun BirthdayStepPreview() {
    MeAppTheme {
        BirthdayStep(
            signupData = SignupData(),
            onBirthdayChange = {}
        )
    }
}
