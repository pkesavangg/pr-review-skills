package com.dmdbrands.gurus.weight.features.myKids.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.Validator
import com.dmdbrands.gurus.weight.features.myKids.strings.AddBabyStrings
import com.dmdbrands.gurus.weight.features.myKids.viewmodel.MyKidsIntent
import com.dmdbrands.gurus.weight.features.myKids.viewmodel.MyKidsViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

private const val MM_PER_INCH = 25.4
private const val MM_PER_CM = 10.0
private const val DECIGRAMS_PER_LB = 4535.92
private const val DECIGRAMS_PER_OZ = 283.495
private const val DECIGRAMS_PER_KG = 10000.0

enum class WeightUnit {
    LBS,
    LBS_OZ,
    KG,
    ;

    val label: String
        get() = when (this) {
            LBS -> AddBabyStrings.UnitSegment.Lbs
            LBS_OZ -> AddBabyStrings.UnitSegment.LbsOz
            KG -> AddBabyStrings.UnitSegment.Kg
        }

    val isMetric: Boolean get() = this == KG

    val lengthSuffix: String
        get() = if (isMetric) AddBabyStrings.UnitSuffix.Cm else AddBabyStrings.UnitSuffix.In
}

@Composable
fun AddBabyScreen(viewModel: MyKidsViewModel = hiltViewModel()) {
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val nameControl = remember {
        FormControl.create(initialValue = "", validators = listOf(FormValidations.required()))
    }
    val birthdayControl = remember {
        FormControl.create<DateTimeValue>(
            initialValue = DateTimeValue.Date(System.currentTimeMillis()),
            validators = emptyList(),
        )
    }
    val sexControl = remember {
        FormControl.create(initialValue = "", validators = listOf(FormValidations.required()))
    }
    val birthLengthControl = remember {
        FormControl.create(initialValue = "", validators = emptyList<Validator<String>>())
    }
    val birthWeightControl = remember {
        FormControl.create(initialValue = "", validators = emptyList<Validator<String>>())
    }
    val birthWeightOzControl = remember {
        FormControl.create(initialValue = "", validators = emptyList<Validator<String>>())
    }

    var weightUnit by remember { mutableStateOf(WeightUnit.LBS) }
    var showSexModal by remember { mutableStateOf(false) }

    val sexOptions = remember {
        listOf(
            RadioButtonOption(id = AddBabyStrings.BiologicalSexModal.Male, label = AddBabyStrings.BiologicalSexModal.Male),
            RadioButtonOption(id = AddBabyStrings.BiologicalSexModal.Female, label = AddBabyStrings.BiologicalSexModal.Female),
            RadioButtonOption(id = AddBabyStrings.BiologicalSexModal.Private, label = AddBabyStrings.BiologicalSexModal.Private),
        )
    }

    if (showSexModal) {
        AppRadioGroupModal(
            title = AddBabyStrings.BiologicalSexModal.Title,
            options = sexOptions,
            selectedItem = sexControl.value.ifEmpty { null },
            confirmText = AddBabyStrings.BiologicalSexModal.Confirm,
            onCancel = { showSexModal = false },
            onOk = { selected ->
                if (selected != null) sexControl.onValueChange(selected)
                showSexModal = false
            },
        )
    }

    AppScaffold(
        title = AddBabyStrings.Title,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                coroutineScope.launch { backStack.removeLast() }
            }
        },
        actions = {
            AppButton(
                label = AddBabyStrings.Save,
                type = ButtonType.InlineTextPrimary,
                size = ButtonSize.Small,
                onClick = {
                    viewModel.handleIntent(
                        MyKidsIntent.SaveBaby(
                            name = nameControl.value,
                            birthdayMillis = (birthdayControl.value as? DateTimeValue.Date)?.millis
                                ?: System.currentTimeMillis(),
                            biologicalSex = sexControl.value.ifEmpty { null },
                            birthLengthMillimeters = parseLengthToMm(birthLengthControl.value, weightUnit),
                            birthWeightDecigrams = parseWeightToDecigrams(
                                primary = birthWeightControl.value,
                                ounces = birthWeightOzControl.value,
                                unit = weightUnit,
                            ),
                        )
                    )
                },
            )
        },
    ) { scaffoldModifier ->
        Column(
            modifier = scaffoldModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md)
                .dismissKeyboardOnTap(),
        ) {
            AppInput(
                formControl = nameControl,
                type = AppInputType.TEXT,
                label = AddBabyStrings.NameLabel,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
            )

            AppText(
                text = AddBabyStrings.BirthdayLabel,
                textType = TextType.Subtitle,
            )
            DateTimeInput(
                formControl = birthdayControl,
                mode = DateTimeInputMode.Date,
                maxValue = DateTimeValue.Date(System.currentTimeMillis()),
            )

            Spacer(modifier = Modifier.height(MeTheme.spacing.md))

            Box(modifier = Modifier.fillMaxWidth()) {
                AppInput(
                    formControl = sexControl,
                    type = AppInputType.TEXT,
                    label = AddBabyStrings.BiologicalSexLabel,
                    readOnly = true,
                    showTrailingIcon = true,
                    showTrailingIconAlways = true,
                    trailingIconId = AppIcons.Filled.CaretDown,
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

            Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

            UnitDecoratedInput(
                formControl = birthLengthControl,
                label = AddBabyStrings.BirthLengthLabel,
                trailingUnit = weightUnit.lengthSuffix,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
            )

            Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

            when (weightUnit) {
                WeightUnit.LBS_OZ -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            UnitDecoratedInput(
                                formControl = birthWeightControl,
                                label = AddBabyStrings.BirthWeightLabel,
                                trailingUnit = AddBabyStrings.UnitSuffix.Lbs,
                                imeAction = ImeAction.Next,
                                onImeAction = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            UnitDecoratedInput(
                                formControl = birthWeightOzControl,
                                label = AddBabyStrings.UnitSuffix.Oz,
                                trailingUnit = AddBabyStrings.UnitSuffix.Oz,
                                imeAction = ImeAction.Done,
                                onImeAction = { focusManager.clearFocus() },
                            )
                        }
                    }
                }

                else -> {
                    UnitDecoratedInput(
                        formControl = birthWeightControl,
                        label = AddBabyStrings.BirthWeightLabel,
                        trailingUnit = if (weightUnit == WeightUnit.KG) {
                            AddBabyStrings.UnitSuffix.Kg
                        } else {
                            AddBabyStrings.UnitSuffix.Lbs
                        },
                        imeAction = ImeAction.Done,
                        onImeAction = { focusManager.clearFocus() },
                    )
                }
            }

            Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                SegmentButtonGroup(
                    data = WeightUnit.entries,
                    selectedData = weightUnit,
                    key = { it.label },
                    onSelected = { weightUnit = it },
                    size = SegmentButtonSize.Small,
                    type = SegmentButtonType.Scrollable,
                    spacedBy = MeTheme.spacing.xs,
                )
            }

            Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

            AppText(
                text = AddBabyStrings.UnitNote,
                textType = TextType.SubHeading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MeTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun UnitDecoratedInput(
    formControl: FormControl<String>,
    label: String,
    trailingUnit: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AppInput(
            formControl = formControl,
            type = AppInputType.NUMERIC_STRING,
            label = label,
            showTrailingIcon = false,
            imeAction = imeAction,
            onImeAction = onImeAction,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = MeTheme.spacing.md),
        ) {
            AppText(
                text = "($trailingUnit)",
                textType = TextType.SubHeading,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = MeTheme.spacing.md),
            )
        }
    }
}

private fun parseLengthToMm(value: String, unit: WeightUnit): Int? {
    val number = value.trim().toDoubleOrNull() ?: return null
    val mm = if (unit.isMetric) number * MM_PER_CM else number * MM_PER_INCH
    return Math.round(mm).toInt()
}

private fun parseWeightToDecigrams(primary: String, ounces: String, unit: WeightUnit): Int? {
    val primaryValue = primary.trim().toDoubleOrNull() ?: return null
    val decigrams = when (unit) {
        WeightUnit.KG -> primaryValue * DECIGRAMS_PER_KG
        WeightUnit.LBS -> primaryValue * DECIGRAMS_PER_LB
        WeightUnit.LBS_OZ -> {
            val ozValue = ounces.trim().toDoubleOrNull() ?: 0.0
            primaryValue * DECIGRAMS_PER_LB + ozValue * DECIGRAMS_PER_OZ
        }
    }
    return Math.round(decigrams).toInt()
}

