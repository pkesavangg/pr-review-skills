package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonData
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.signup.model.BabyFormControls
import com.dmdbrands.gurus.weight.features.signup.model.BabyWeightUnit
import com.dmdbrands.gurus.weight.features.signup.strings.BabySignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

private fun BabyWeightUnit.segmentLabel(): String = when (this) {
    BabyWeightUnit.LBS -> BabySignupStrings.unitLbs
    BabyWeightUnit.LBS_OZ -> BabySignupStrings.unitLbsOz
    BabyWeightUnit.KG -> BabySignupStrings.unitKg
}

private fun BabyWeightUnit.weightSuffix(): String = when (this) {
    BabyWeightUnit.KG -> BabySignupStrings.unitKg
    else -> BabySignupStrings.unitLbs
}

private fun BabyWeightUnit.lengthSuffix(): String =
    if (this == BabyWeightUnit.KG) BabySignupStrings.unitCm else BabySignupStrings.unitIn

/**
 * Step for collecting baby information during signup.
 */
@Composable
fun AddBabyStep(
    babyForm: BabyFormControls,
    modifier: Modifier = Modifier,
) {
    val birthLengthFocusRequester = remember { FocusRequester() }
    val birthWeightFocusRequester = remember { FocusRequester() }
    val birthWeightOzFocusRequester = remember { FocusRequester() }
    var showSexModal by remember { mutableStateOf(false) }

    val weightUnit = babyForm.weightUnit.value

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

        AppInput(
            formControl = babyForm.name,
            type = AppInputType.TEXT,
            label = BabySignupStrings.nameLabel,
            imeAction = ImeAction.Next,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        AppText(BabySignupStrings.birthdayLabel, TextType.Subtitle, spacing = MeTheme.spacing.xs)
        DateTimeInput(
            formControl = babyForm.birthday,
            mode = DateTimeInputMode.Date,
            maxValue = DateTimeValue.Date(System.currentTimeMillis()),
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

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

        AppInput(
            formControl = babyForm.birthLength,
            type = AppInputType.NUMERIC_STRING,
            label = BabySignupStrings.birthLengthDynamic.format(weightUnit.lengthSuffix()),
            imeAction = ImeAction.Next,
            nextFocusRequester = birthWeightFocusRequester,
            modifier = Modifier.focusRequester(birthLengthFocusRequester),
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        when (weightUnit) {
            BabyWeightUnit.LBS_OZ -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppInput(
                            formControl = babyForm.birthWeight,
                            type = AppInputType.NUMERIC_STRING,
                            label = BabySignupStrings.birthWeightDynamic.format(BabySignupStrings.unitLbs),
                            imeAction = ImeAction.Next,
                            nextFocusRequester = birthWeightOzFocusRequester,
                            modifier = Modifier.focusRequester(birthWeightFocusRequester),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppInput(
                            formControl = babyForm.birthWeightOz,
                            type = AppInputType.NUMERIC_STRING,
                            label = BabySignupStrings.birthWeightOzLabel,
                            imeAction = ImeAction.Done,
                            modifier = Modifier.focusRequester(birthWeightOzFocusRequester),
                        )
                    }
                }
            }

            else -> {
                AppInput(
                    formControl = babyForm.birthWeight,
                    type = AppInputType.NUMERIC_STRING,
                    label = BabySignupStrings.birthWeightDynamic.format(weightUnit.weightSuffix()),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.focusRequester(birthWeightFocusRequester),
                )
            }
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        val unitOptions = remember {
            BabyWeightUnit.entries.map { unit ->
                SegmentButtonData(id = unit.ordinal, label = unit.segmentLabel())
            }
        }
        val selectedOption = unitOptions[weightUnit.ordinal]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            SegmentButtonGroup(
                data = unitOptions,
                selectedData = selectedOption,
                key = SegmentButtonData::label,
                onSelected = { option ->
                    babyForm.weightUnit.onValueChange(BabyWeightUnit.entries[option.id])
                },
                size = SegmentButtonSize.Small,
                type = SegmentButtonType.Scrollable,
                spacedBy = MeTheme.spacing.xs,
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        AppText(
            text = BabySignupStrings.unitNote,
            textType = TextType.SubHeading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeTheme.spacing.sm),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
