package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.signup.model.BabyFormControls
import com.dmdbrands.gurus.weight.features.signup.strings.BabySignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Step for collecting baby information during signup.
 * Shows a form with name, birthday, biological sex, birth length, and birth weight fields.
 */
@Composable
fun AddBabyStep(
    babyForm: BabyFormControls,
    modifier: Modifier = Modifier,
) {
    val birthLengthFocusRequester = remember { FocusRequester() }
    val birthWeightFocusRequester = remember { FocusRequester() }
    var showSexModal by remember { mutableStateOf(false) }

    val sexOptions = remember {
        listOf(
            RadioButtonOption(id = BabySignupStrings.male, label = BabySignupStrings.male),
            RadioButtonOption(id = BabySignupStrings.female, label = BabySignupStrings.female),
            RadioButtonOption(id = BabySignupStrings.other, label = BabySignupStrings.other),
        )
    }

    if (showSexModal) {
        AppRadioGroupModal(
            title = BabySignupStrings.selectSexTitle,
            options = sexOptions,
            selectedItem = babyForm.biologicalSex.value.ifEmpty { null },
            onCancel = { showSexModal = false },
            onOk = { selected ->
                if (selected != null) {
                    babyForm.biologicalSex.onValueChange(selected)
                }
                showSexModal = false
            },
        )
    }

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
        modifier = modifier,
    ) {
        AppText(BabySignupStrings.addBabyTitle, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(BabySignupStrings.addBabySubtitle, TextType.Subtitle, spacing = MeTheme.spacing.lg)

        // Name input
        AppInput(
            formControl = babyForm.name,
            type = AppInputType.TEXT,
            label = BabySignupStrings.nameLabel,
            imeAction = ImeAction.Next,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        // Birthday date picker
        AppText(BabySignupStrings.birthdayLabel, TextType.Subtitle, spacing = MeTheme.spacing.xs)
        DateTimeInput(
            formControl = babyForm.birthday,
            mode = DateTimeInputMode.Date,
            maxValue = DateTimeValue.Date(System.currentTimeMillis()),
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        // Biological Sex selector (opens modal on click)
        Box(modifier = Modifier.fillMaxWidth()) {
            AppInput(
                formControl = babyForm.biologicalSex,
                type = AppInputType.TEXT,
                label = BabySignupStrings.biologicalSexLabel,
                readOnly = true,
                showTrailingIcon = true,
                showTrailingIconAlways = true,
                trailingIconId = com.dmdbrands.gurus.weight.R.drawable.ic_chevron_down,
                onTrailingAction = { showSexModal = true },
            )
            // Transparent overlay to capture clicks since TextField consumes touch events
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { showSexModal = true },
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.md))

        // Birth length (optional)
        AppInput(
            formControl = babyForm.birthLength,
            type = AppInputType.NUMERIC_STRING,
            label = BabySignupStrings.birthLengthLabel,
            imeAction = ImeAction.Next,
            nextFocusRequester = birthWeightFocusRequester,
            modifier = Modifier.focusRequester(birthLengthFocusRequester),
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        // Birth weight (optional)
        AppInput(
            formControl = babyForm.birthWeight,
            type = AppInputType.NUMERIC_STRING,
            label = BabySignupStrings.birthWeightLabel,
            imeAction = ImeAction.Done,
            modifier = Modifier.focusRequester(birthWeightFocusRequester),
        )
    }
}

@PreviewTheme
@Composable
fun AddBabyStepPreview() {
    MeAppTheme {
        AddBabyStep(
            babyForm = BabyFormControls.create(),
        )
    }
}
