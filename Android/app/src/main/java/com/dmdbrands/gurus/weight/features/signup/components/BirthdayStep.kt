package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeUtil
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Step for collecting user's birthday
 */
@Composable
fun BirthdayStep(birthdayControl: FormControl<DateTimeValue>) {
  AppStyledCard(
    cardAlignmentType = LocalCardAlignment.current,
  ) {
    // TalkBack: the step title is a heading for by-heading navigation.
    AppText(
      SignupStrings.birthdayStepTitle,
      TextType.Title,
      spacing = MeTheme.spacing.xs,
      modifier = Modifier.semantics { heading() },
    )
    AppText(SignupStrings.birthdayStepSubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)
    DateTimeInput(
      formControl = birthdayControl,
      mode = DateTimeInputMode.Date,
      maxValue = DateTimeValue.Date(DateTimeUtil.getMinBirthdayOffsetForDatePicker()),
    )
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
