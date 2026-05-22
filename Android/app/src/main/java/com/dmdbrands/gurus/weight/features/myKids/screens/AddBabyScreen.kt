package com.dmdbrands.gurus.weight.features.myKids.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputDefaults
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppPicker
import com.dmdbrands.gurus.weight.features.common.components.AppRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.BaseModal
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.components.rememberPickerState
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.myKids.strings.AddBabyStrings
import com.dmdbrands.gurus.weight.features.myKids.viewmodel.MyKidsIntent
import com.dmdbrands.gurus.weight.features.myKids.viewmodel.MyKidsViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

private val BIRTH_WEIGHT_LBS = (0..25).toList()
private val BIRTH_WEIGHT_OZ = (0..15).toList()
private val BIRTH_LENGTH_IN = (1..36).toList()

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
        FormControl.create(initialValue = "", validators = emptyList<com.dmdbrands.gurus.weight.features.common.helper.form.Validator<String>>())
    }
    val birthWeightControl = remember {
        FormControl.create(initialValue = "", validators = emptyList<com.dmdbrands.gurus.weight.features.common.helper.form.Validator<String>>())
    }

    var showSexModal by remember { mutableStateOf(false) }
    var showLengthModal by remember { mutableStateOf(false) }
    var showWeightModal by remember { mutableStateOf(false) }

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

    if (showLengthModal) {
        BirthLengthPickerModal(
            selectedInches = birthLengthControl.value.removeSuffix(" ${AddBabyStrings.BirthLengthModal.InchSuffix}").toIntOrNull() ?: 18,
            onCancel = { showLengthModal = false },
            onOk = { inches ->
                birthLengthControl.onValueChange("$inches ${AddBabyStrings.BirthLengthModal.InchSuffix}")
                showLengthModal = false
            },
        )
    }

    if (showWeightModal) {
        val parts = birthWeightControl.value.split(" ")
        val currentLbs = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val currentOz = parts.getOrNull(2)?.toIntOrNull() ?: 0
        BirthWeightPickerModal(
            selectedLbs = currentLbs,
            selectedOz = currentOz,
            onCancel = { showWeightModal = false },
            onOk = { lbs, oz ->
                birthWeightControl.onValueChange(
                    "$lbs ${AddBabyStrings.BirthWeightModal.LbSuffix} $oz ${AddBabyStrings.BirthWeightModal.OzSuffix}"
                )
                showWeightModal = false
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
                            birthLengthMillimeters = parseLengthToMm(birthLengthControl.value),
                            birthWeightDecigrams = parseWeightToDecigrams(birthWeightControl.value),
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
            // AppInput already has a built-in xs spacer + error placeholder at the bottom,
            // so we use no extra spacing here — just the birthday label immediately after.
            AppInput(
                formControl = nameControl,
                type = AppInputType.TEXT,
                label = AddBabyStrings.NameLabel,
                imeAction = ImeAction.Done,
                onImeAction = { focusManager.clearFocus() },
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

            ReadOnlyUnitField(
                formControl = birthLengthControl,
                label = AddBabyStrings.BirthLengthLabel,
                trailingUnit = AddBabyStrings.FixedLengthUnit,
                onClick = { showLengthModal = true },
            )

            Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

            ReadOnlyUnitField(
                formControl = birthWeightControl,
                label = AddBabyStrings.BirthWeightLabel,
                trailingUnit = AddBabyStrings.FixedWeightUnit,
                onClick = { showWeightModal = true },
            )
        }
    }
}

@Composable
private fun ReadOnlyUnitField(
    formControl: FormControl<String>,
    label: String,
    trailingUnit: String,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AppInput(
            formControl = formControl,
            type = AppInputType.TEXT,
            label = label,
            readOnly = true,
            showTrailingIcon = false,
            trailingText = trailingUnit,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onClick() },
        )
    }
}

@Composable
private fun BirthWeightPickerModal(
    selectedLbs: Int,
    selectedOz: Int,
    onCancel: () -> Unit,
    onOk: (lbs: Int, oz: Int) -> Unit,
) {
    val lbsState = rememberPickerState(selectedLbs)
    val ozState = rememberPickerState(selectedOz)
    val itemHeight = (MeTheme.spacing.sm * 2) + 24.dp

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BaseModal(
            title = AddBabyStrings.BirthWeightModal.Title,
            primaryAction = com.dmdbrands.gurus.weight.features.common.model.ActionButton(
                text = AddBabyStrings.ModalConfirm,
                action = { onOk(lbsState.item, ozState.item) },
            ),
            secondaryAction = com.dmdbrands.gurus.weight.features.common.model.ActionButton(
                text = AddBabyStrings.ModalCancel,
                action = onCancel,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                AppPicker(
                    items = BIRTH_WEIGHT_LBS,
                    selectedItem = lbsState.item,
                    onItemSelected = { lbsState.setItem(it) },
                    labelMapper = { it, _ -> "$it ${AddBabyStrings.BirthWeightModal.LbSuffix}" },
                    itemHeight = itemHeight,
                    modifier = Modifier.weight(1f),
                )
                AppPicker(
                    items = BIRTH_WEIGHT_OZ,
                    selectedItem = ozState.item,
                    onItemSelected = { ozState.setItem(it) },
                    labelMapper = { it, _ -> "$it ${AddBabyStrings.BirthWeightModal.OzSuffix}" },
                    itemHeight = itemHeight,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BirthLengthPickerModal(
    selectedInches: Int,
    onCancel: () -> Unit,
    onOk: (inches: Int) -> Unit,
) {
    val inchState = rememberPickerState(selectedInches)
    val itemHeight = (MeTheme.spacing.sm * 2) + 24.dp

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BaseModal(
            title = AddBabyStrings.BirthLengthModal.Title,
            primaryAction = com.dmdbrands.gurus.weight.features.common.model.ActionButton(
                text = AddBabyStrings.ModalConfirm,
                action = { onOk(inchState.item) },
            ),
            secondaryAction = com.dmdbrands.gurus.weight.features.common.model.ActionButton(
                text = AddBabyStrings.ModalCancel,
                action = onCancel,
            ),
        ) {
            AppPicker(
                items = BIRTH_LENGTH_IN,
                selectedItem = inchState.item,
                onItemSelected = { inchState.setItem(it) },
                labelMapper = { it, _ -> "$it ${AddBabyStrings.BirthLengthModal.InchSuffix}" },
                itemHeight = itemHeight,
            )
        }
    }
}

private fun parseWeightToDecigrams(value: String): Int? {
    if (value.isBlank()) return null
    val parts = value.split(" ")
    val lbs = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val oz = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return Math.round(lbs * 4535.92 + oz * 283.495).toInt()
}

private fun parseLengthToMm(value: String): Int? {
    if (value.isBlank()) return null
    val inches = value.split(" ").getOrNull(0)?.toIntOrNull() ?: return null
    return Math.round(inches * 25.4).toInt()
}

@PreviewTheme
@Composable
fun AddBabyScreenPreview() {
    MeAppTheme {
        BirthWeightPickerModal(
            selectedLbs = 6,
            selectedOz = 8,
            onCancel = {},
            onOk = { _, _ -> },
        )
    }
}

@PreviewTheme
@Composable
fun BirthLengthPickerModalPreview() {
    MeAppTheme {
        BirthLengthPickerModal(
            selectedInches = 6,
            onCancel = {},
            onOk = {},
        )
    }
}
