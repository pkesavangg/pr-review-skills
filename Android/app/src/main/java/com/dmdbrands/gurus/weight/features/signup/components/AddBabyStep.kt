package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputDefaults
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
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
    isEditing: Boolean = false,
    onOpenSexPicker: () -> Unit = {},
) {
    val birthLengthFocusRequester = remember { FocusRequester() }
    val birthWeightFocusRequester = remember { FocusRequester() }
    val birthWeightOzFocusRequester = remember { FocusRequester() }

    val weightUnit = babyForm.weightUnit.value

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
        modifier = modifier,
    ) {
        AppText(
            if (isEditing) BabySignupStrings.editBabyTitle else BabySignupStrings.addBabyTitle,
            TextType.Title,
            spacing = MeTheme.spacing.xs,
        )
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

        BabySexField(formControl = babyForm.biologicalSex, onOpenSexPicker = onOpenSexPicker)

        Spacer(modifier = Modifier.height(MeTheme.spacing.md))

        UnitDecoratedInput(
            formControl = babyForm.birthLength,
            label = BabySignupStrings.birthLengthLabel,
            trailingUnit = weightUnit.lengthSuffix(),
            imeAction = ImeAction.Next,
            nextFocusRequester = birthWeightFocusRequester,
            focusRequester = birthLengthFocusRequester,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        BabyBirthWeightInput(
            babyForm = babyForm,
            weightUnit = weightUnit,
            birthWeightFocusRequester = birthWeightFocusRequester,
            birthWeightOzFocusRequester = birthWeightOzFocusRequester,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        BabyUnitSelector(
            weightUnit = weightUnit,
            onUnitChange = { babyForm.weightUnit.onValueChange(it) },
        )
    }
}

/** Read-only Biological Sex field that opens the shared radio picker (dropdown caret). */
@Composable
private fun BabySexField(
    formControl: FormControl<String>,
    onOpenSexPicker: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AppInput(
            formControl = formControl,
            type = AppInputType.TEXT,
            label = BabySignupStrings.biologicalSexLabel,
            readOnly = true,
            showTrailingIcon = true,
            showTrailingIconAlways = true,
            trailingIconId = com.dmdbrands.gurus.weight.R.drawable.ic_filled_caret_down,
            onTrailingAction = onOpenSexPicker,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onOpenSexPicker() },
        )
    }
}

/** Birth-weight input: a single field, or lbs + oz side-by-side when the unit is lbs/oz. */
@Composable
private fun BabyBirthWeightInput(
    babyForm: BabyFormControls,
    weightUnit: BabyWeightUnit,
    birthWeightFocusRequester: FocusRequester,
    birthWeightOzFocusRequester: FocusRequester,
) {
    when (weightUnit) {
        BabyWeightUnit.LBS_OZ -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
            ) {
                // Column (not Box) so AppInput's siblings — field, supporting/error line, and
                // bottom spacer — stack vertically and keep the bottom padding, matching the
                // single-field layout. A Box would stack them, dropping the bottom padding.
                Column(modifier = Modifier.weight(1f)) {
                    UnitDecoratedInput(
                        formControl = babyForm.birthWeight,
                        label = BabySignupStrings.birthWeightLabel,
                        trailingUnit = BabySignupStrings.unitLbs,
                        imeAction = ImeAction.Next,
                        nextFocusRequester = birthWeightOzFocusRequester,
                        focusRequester = birthWeightFocusRequester,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    UnitDecoratedInput(
                        formControl = babyForm.birthWeightOz,
                        label = BabySignupStrings.birthWeightOzLabel,
                        trailingUnit = BabySignupStrings.unitOz,
                        imeAction = ImeAction.Done,
                        focusRequester = birthWeightOzFocusRequester,
                    )
                }
            }
        }

        else -> {
            UnitDecoratedInput(
                formControl = babyForm.birthWeight,
                label = BabySignupStrings.birthWeightLabel,
                trailingUnit = weightUnit.weightSuffix(),
                imeAction = ImeAction.Done,
                focusRequester = birthWeightFocusRequester,
            )
        }
    }
}

/** The lbs / lbs+oz / kg segmented unit selector plus the explanatory note. */
@Composable
private fun BabyUnitSelector(
    weightUnit: BabyWeightUnit,
    onUnitChange: (BabyWeightUnit) -> Unit,
) {
    val unitOptions = remember {
        BabyWeightUnit.entries.map { unit ->
            SegmentButtonData(id = unit.ordinal, label = unit.segmentLabel())
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        SegmentButtonGroup(
            data = unitOptions,
            selectedData = unitOptions[weightUnit.ordinal],
            key = SegmentButtonData::label,
            onSelected = { option -> onUnitChange(BabyWeightUnit.entries[option.id]) },
            size = SegmentButtonSize.Small,
            type = SegmentButtonType.Scrollable,
            spacedBy = MeTheme.spacing.xs,
            uppercaseLabels = false,
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

@Composable
private fun UnitDecoratedInput(
    formControl: FormControl<String>,
    label: String,
    trailingUnit: String,
    imeAction: ImeAction,
    focusRequester: FocusRequester,
    nextFocusRequester: FocusRequester? = null,
) {
    AppInput(
        formControl = formControl,
        type = AppInputType.NUMERIC_STRING,
        label = label,
        trailingText = trailingUnit,
        showTrailingIcon = false,
        imeAction = imeAction,
        nextFocusRequester = nextFocusRequester,
        modifier = Modifier.focusRequester(focusRequester),
    )
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
