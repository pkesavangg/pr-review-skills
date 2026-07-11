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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputDefaults
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
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
import com.dmdbrands.gurus.weight.features.common.components.BiologicalSexOptions
import com.dmdbrands.gurus.weight.features.common.components.TextType
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
fun AddBabyScreen(babyId: String? = null, viewModel: MyKidsViewModel = hiltViewModel()) {
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isEditing = babyId != null

    // Weight/length inputs follow the account's unit (no toggle, unlike sign-up):
    //  - metric           → kg (1 field)  + cm
    //  - imperial lb+oz    → lb + oz (2 fields) + in
    //  - imperial lb-dec   → lb decimal (1 field) + in
    val isMetric = state.measurementUnits == MeasurementUnits.METRIC
    val isLbOz = state.measurementUnits == MeasurementUnits.IMPERIAL_LB_OZ

    val controls = rememberBabyFormControls()

    // Edit mode: seed the form once from the existing baby (when it loads from the VM flow).
    val editingBaby = remember(babyId, state.babies) {
        babyId?.let { id -> state.babies.firstOrNull { it.id == id } }
    }
    var hasSeeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editingBaby, isMetric, isLbOz) {
        val baby = editingBaby ?: return@LaunchedEffect
        if (hasSeeded) return@LaunchedEffect
        hasSeeded = true
        seedBabyControls(controls, baby, isMetric, isLbOz)
    }

    var showSexModal by remember { mutableStateOf(false) }
    if (showSexModal) {
        BabySexModal(sexControl = controls.sex, onDismiss = { showSexModal = false })
    }

    AppScaffold(
        title = if (isEditing) AddBabyStrings.EditTitle else AddBabyStrings.Title,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close, contentDescription = AddBabyStrings.accCloseLabel) {
                coroutineScope.launch { backStack.removeLast() }
            }
        },
        actions = {
            AppButton(
                label = AddBabyStrings.Save,
                type = ButtonType.InlineTextPrimary,
                size = ButtonSize.Small,
                onClick = {
                    viewModel.handleIntent(controls.toSaveBabyIntent(babyId, isMetric, isLbOz))
                },
            )
        },
    ) { scaffoldModifier ->
        AddBabyFields(
            modifier = scaffoldModifier,
            controls = controls,
            isMetric = isMetric,
            isLbOz = isLbOz,
            onOpenSexModal = { showSexModal = true },
        )
    }
}

/** Form-control holder for the add/edit baby screen. */
private data class BabyFormControls(
    val name: FormControl<String>,
    val birthday: FormControl<DateTimeValue>,
    val sex: FormControl<String>,
    val length: FormControl<String>,
    val weight: FormControl<String>,
    val weightOz: FormControl<String>,
)

@Composable
private fun rememberBabyFormControls(): BabyFormControls {
    // Typed inputs (no pickers). Bounds mirror babyApp's add-a-baby validation
    // (lb/kg ≤ ~999/450, oz < 16, length ≤ ~999); blank is allowed (optional fields).
    val name = remember { FormControl.create("", listOf(FormValidations.required())) }
    val birthday = remember {
        FormControl.create<DateTimeValue>(DateTimeValue.Date(System.currentTimeMillis()), emptyList())
    }
    val sex = remember { FormControl.create("", listOf(FormValidations.required())) }
    val length = remember { FormControl.create("", listOf(FormValidations.decimalRangeValidator(0, 1000))) }
    val weight = remember { FormControl.create("", listOf(FormValidations.decimalRangeValidator(0, 1000))) }
    val weightOz = remember { FormControl.create("", listOf(FormValidations.decimalRangeValidator(0, 16))) }
    return remember { BabyFormControls(name, birthday, sex, length, weight, weightOz) }
}

/** Maps the form to a SaveBaby intent, converting weight/length per the account's unit. */
private fun BabyFormControls.toSaveBabyIntent(babyId: String?, isMetric: Boolean, isLbOz: Boolean) =
    MyKidsIntent.SaveBaby(
        name = name.value,
        birthdayMillis = (birthday.value as? DateTimeValue.Date)?.millis ?: System.currentTimeMillis(),
        // Persist the canonical lowercase API value ("male"), not the capitalized display
        // label ("Male"), so it matches §2.8 and the percentile/display logic across the app.
        biologicalSex = sex.value.ifEmpty { null }?.lowercase(),
        birthLengthMillimeters = lengthToMm(length.value, isMetric),
        birthWeightDecigrams = weightToDecigrams(weight.value, weightOz.value, isMetric, isLbOz),
        babyId = babyId,
    )

/** Edit mode: seeds the form from an existing baby, in the account's unit. */
private fun seedBabyControls(controls: BabyFormControls, baby: BabyProfile, isMetric: Boolean, isLbOz: Boolean) {
    controls.name.onValueChange(baby.name)
    baby.birthdate?.let { iso ->
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)?.time }
            .getOrNull()?.let { controls.birthday.onValueChange(DateTimeValue.Date(it)) }
    }
    // Stored/API sex is lowercase ("male"); the radio options + field show capitalized
    // labels ("Male"), so capitalize for the field value to preselect + display correctly.
    baby.sex?.takeIf { it.isNotBlank() }
        ?.let { sex -> controls.sex.onValueChange(sex.replaceFirstChar { c -> c.uppercase() }) }
    baby.birthWeightDecigrams?.takeIf { it > 0 }?.let { dg ->
        when {
            isLbOz -> {
                val (lb, oz) = ConversionTools.convertDecigramsToLbOz(dg)
                controls.weight.onValueChange(lb.toString())
                controls.weightOz.onValueChange(formatOneDecimal(oz))
            }
            isMetric -> controls.weight.onValueChange(formatOneDecimal(ConversionTools.convertDecigramsToKg(dg)))
            else -> controls.weight.onValueChange(formatOneDecimal(ConversionTools.convertDecigramsToLbExact(dg)))
        }
    }
    baby.birthLengthMillimeters?.takeIf { it > 0 }?.let { mm ->
        val value = if (isMetric) ConversionTools.convertMmToCm(mm) else ConversionTools.convertMmToInches(mm)
        controls.length.onValueChange(formatOneDecimal(value))
    }
}

/** Biological-sex picker modal (capitalized labels; writes the selected label to [sexControl]). */
@Composable
private fun BabySexModal(sexControl: FormControl<String>, onDismiss: () -> Unit) {
    val options = remember { BiologicalSexOptions.options() }
    AppRadioGroupModal(
        title = BiologicalSexOptions.Title,
        options = options,
        selectedItem = sexControl.value.ifEmpty { null },
        confirmText = AddBabyStrings.BiologicalSexModal.Confirm,
        onCancel = onDismiss,
        onOk = { selected ->
            if (selected != null) sexControl.onValueChange(selected)
            onDismiss()
        },
    )
}

/** The scrollable form fields (name, birthday, sex, length, weight) for the add/edit baby screen. */
@Composable
private fun AddBabyFields(
    modifier: Modifier,
    controls: BabyFormControls,
    isMetric: Boolean,
    isLbOz: Boolean,
    onOpenSexModal: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md)
            .dismissKeyboardOnTap(),
    ) {
        AppInput(
            formControl = controls.name,
            type = AppInputType.TEXT,
            label = AddBabyStrings.NameLabel,
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus() },
        )

        AppText(text = AddBabyStrings.BirthdayLabel, textType = TextType.Subtitle)
        DateTimeInput(
            formControl = controls.birthday,
            mode = DateTimeInputMode.Date,
            maxValue = DateTimeValue.Date(System.currentTimeMillis()),
            // DOB: calendar grid only to prevent silent leap-day normalization. (MOB-868)
            showModeToggle = false,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.md))

        BabySexField(sexControl = controls.sex, onOpenSexModal = onOpenSexModal)

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        // Length — typed, unit per account (cm metric / in imperial).
        AppInput(
            formControl = controls.length,
            type = AppInputType.DECIMAL_STRING,
            label = AddBabyStrings.BirthLengthLabel,
            trailingText = if (isMetric) AddBabyStrings.CmUnit else AddBabyStrings.FixedLengthUnit,
            imeAction = ImeAction.Next,
            maxLength = 5,
        )

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

        BirthWeightInput(controls.weight, controls.weightOz, isMetric, isLbOz, focusManager)
    }
}

/** Read-only Biological Sex field that opens the radio picker. */
@Composable
private fun BabySexField(sexControl: FormControl<String>, onOpenSexModal: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AppInput(
            formControl = sexControl,
            type = AppInputType.TEXT,
            label = AddBabyStrings.BiologicalSexLabel,
            readOnly = true,
            showTrailingIcon = true,
            showTrailingIconAlways = true,
            trailingIconId = AppIcons.Filled.CaretDown,
            onTrailingAction = onOpenSexModal,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onOpenSexModal() },
        )
    }
}

/** Birth-weight input: two fields (lb + oz) for lb+oz units, otherwise one decimal field. */
@Composable
private fun BirthWeightInput(
    weightControl: FormControl<String>,
    ozControl: FormControl<String>,
    isMetric: Boolean,
    isLbOz: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager,
) {
    if (isLbOz) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppInput(
                    formControl = weightControl,
                    type = AppInputType.NUMERIC_STRING,
                    label = AddBabyStrings.BirthWeightLabel,
                    trailingText = AddBabyStrings.BirthWeightModal.LbSuffix,
                    imeAction = ImeAction.Next,
                    maxLength = 3,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                AppInput(
                    formControl = ozControl,
                    type = AppInputType.DECIMAL_STRING,
                    label = AddBabyStrings.BirthWeightLabel,
                    trailingText = AddBabyStrings.BirthWeightModal.OzSuffix,
                    imeAction = ImeAction.Done,
                    onImeAction = { focusManager.clearFocus() },
                    maxLength = 4,
                )
            }
        }
    } else {
        AppInput(
            formControl = weightControl,
            type = AppInputType.DECIMAL_STRING,
            label = AddBabyStrings.BirthWeightLabel,
            trailingText = if (isMetric) AddBabyStrings.KgUnit else AddBabyStrings.FixedWeightUnit,
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus() },
            maxLength = 6,
        )
    }
}

/**
 * Converts the typed birth weight to decigrams per the account's unit (mirrors sign-up):
 * metric → kg, imperial lb+oz → two fields, imperial decimal → lb. Returns null when empty.
 */
private fun weightToDecigrams(weight: String, ounces: String, isMetric: Boolean, isLbOz: Boolean): Int? {
    if (isLbOz) {
        val lbs = weight.toIntOrNull() ?: 0
        val oz = ounces.toDoubleOrNull() ?: 0.0
        if (lbs <= 0 && oz <= 0.0) return null
        return ConversionTools.convertLbOzToDecigrams(lbs, oz)
    }
    val value = weight.toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return if (isMetric) ConversionTools.convertKgToDecigrams(value) else ConversionTools.convertLbToDecigrams(value)
}

/** Converts the typed birth length to millimeters per the account's unit (cm metric / in imperial). */
private fun lengthToMm(value: String, isMetric: Boolean): Int? {
    val v = value.toDoubleOrNull() ?: return null
    if (v <= 0.0) return null
    return if (isMetric) ConversionTools.convertCmToMm(v) else ConversionTools.convertInchesToMm(v)
}

/** Seeds a decimal field as "8" (whole) or "14.9" (1 decimal), never "8.0". */
private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
