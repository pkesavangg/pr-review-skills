package com.greatergoods.meapp.features.signup.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppStyledCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.DateTimeInput
import com.greatergoods.meapp.features.common.components.DateTimeInputMode
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.common.helper.DateTimeTools
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Step for collecting user's birthday
 */
@Composable
fun BirthdayStep(
    birthdayControl: FormControl<String>,
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    // Helper function to convert string to DateTimeValue.Date
    fun stringToDateTimeValue(dateString: String): DateTimeValue.Date? {
        return try {
            val date = dateTimeFormat.parse(dateString)
            date?.let { DateTimeValue.Date(it.time) }
        } catch (e: Exception) {
            null
        }
    }

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current
    ) {
        AppText(SignupStrings.birthdayStepTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(SignupStrings.birthdayStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.md)
        DateTimeInput(
            value = try {
                val date = dateFormat.parse(birthdayControl.value)
                date?.let { DateTimeValue.Date(it.time) }
            } catch (e: Exception) {
                null
            },
            onValueChange = { dateTimeValue ->
                if (dateTimeValue is DateTimeValue.Date) {
                    val formattedDate = dateFormat.format(Date(dateTimeValue.millis))
                    birthdayControl.onValueChange(formattedDate)
                }
            },
            mode = DateTimeInputMode.Date,
            minValue = stringToDateTimeValue(DateTimeTools.getMaxBirthdayOffsetForDatePicker()), // 120 years ago (oldest)
            maxValue = stringToDateTimeValue(DateTimeTools.getMinBirthdayOffsetForDatePicker()), // 13 years ago (youngest)
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
    }
}

@PreviewTheme
@Composable
fun BirthdayStepPreview() {
    MeAppTheme {
        BirthdayStep(
            birthdayControl = FormControl.create("2000-01-01", listOf(FormValidations.required())),
        )
    }
}
