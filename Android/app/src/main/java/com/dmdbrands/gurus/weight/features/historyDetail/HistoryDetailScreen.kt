package com.dmdbrands.gurus.weight.features.historyDetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import com.dmdbrands.gurus.weight.features.common.components.AppBottomSheet
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppTextArea
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntrySource
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.manualEntry.components.BabyEntrySection
import com.dmdbrands.gurus.weight.features.manualEntry.components.BloodPressureSection
import com.dmdbrands.gurus.weight.features.manualEntry.components.ExpandableMetricsCard
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.BloodPressureEntryForm
import kotlin.math.roundToInt
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.BabyEntryForm
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.EntryForm
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.features.historyDetail.components.BabyDayHistoryList
import com.dmdbrands.gurus.weight.features.historyDetail.components.BpHistoryDetailList
import com.dmdbrands.gurus.weight.features.historyDetail.components.WeightHistoryDetailList
import com.dmdbrands.gurus.weight.features.historyDetail.viewmodel.HistoryDetailIntent
import com.dmdbrands.gurus.weight.features.historyDetail.viewmodel.HistoryDetailState
import com.dmdbrands.gurus.weight.features.historyDetail.viewmodel.HistoryDetailViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun HistoryDetailScreen(
    monthKey: String,
    productType: ProductType =
        ProductType.MY_WEIGHT,
) {
    val viewModel: HistoryDetailViewModel = hiltViewModel<HistoryDetailViewModel, HistoryDetailViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(monthKey, productType)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing = state.isLoading

    HistoryDetailScreenContent(
        state = state,
        productType = productType,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.handleIntent(HistoryDetailIntent.Refresh) },
        handleIntent = viewModel::handleIntent,
    )
}

@Composable
fun HistoryDetailScreenContent(
    state: HistoryDetailState,
    productType: ProductType =
        ProductType.MY_WEIGHT,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    handleIntent: (HistoryDetailIntent) -> Unit,
) {
    val backStack = LocalNavBackStack.current
    val scope = rememberCoroutineScope()
    // Pop-back when the last entry in this month/day is deleted is owned by the ViewModel
    // (HistoryDetailViewModel.loadDetail → navigationService.navigateBack), matching the app's
    // ViewModel-driven navigation convention. (MOB-1173)
    AppScaffold(
        title = state.month,
        // On the baby's birth-date day-detail, show the birthday balloon beside the title (the
        // title slot is centered by AppBar). Otherwise the plain string title is used.
        topBarContent = if (state.showBirthdayBalloon) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        id = AppIcons.Default.BirthdayBalloon,
                        contentDescription = HistoryItemStrings.BirthdayBalloonContentDescription,
                        type = AppIconType.Default,
                        onClick = null,
                        modifier = Modifier.padding(end = MeTheme.spacing.x2s),
                    )
                    Text(
                        text = state.month,
                        style = MeTheme.typography.heading5,
                        color = MeTheme.colorScheme.textHeading,
                    )
                }
            }
        } else {
            null
        },
        isRefreshing = state.isLoading,
        navigationIcon = {
            AppIconButton(
                AppIcons.Default.Close,
                contentDescription = HistoryDetailScreenStrings.BackButtonContentDescription,
            ) {
                scope.launch {
                    backStack.removeLast()
                }
            }
        },
        onRefresh = onRefresh,
    ) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            when {
                state.isLoading && !isRefreshing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    when (productType) {
                        ProductType.BLOOD_PRESSURE -> {
                            BpHistoryDetailList(
                                entries = state.historyItems.filterIsInstance<BpmEntry>(),
                                expandedIds = state.itemsOpened,
                                onToggleExpand = { id ->
                                    val newIds = if (state.itemsOpened.contains(id)) {
                                        state.itemsOpened.filter { it != id }
                                    } else {
                                        state.itemsOpened + id
                                    }
                                    handleIntent(HistoryDetailIntent.SetItemsOpened(newIds))
                                },
                                // MOB-1173: manual = values+note editable, device-synced = note-only
                                // (values disabled) — same sheet, gated by source.
                                onEditEntry = { handleIntent(HistoryDetailIntent.EditBpEntry(it)) },
                                onItemDelete = { handleIntent(HistoryDetailIntent.DeleteEntry(it)) },
                            )
                        }
                        ProductType.BABY -> {
                            BabyDayHistoryList(
                                entries = state.historyItems.filterIsInstance<BabyEntry>(),
                                isMetric = state.isMetric,
                                onEditEntry = { handleIntent(HistoryDetailIntent.EditBabyEntry(it)) },
                                onItemDelete = { handleIntent(HistoryDetailIntent.DeleteEntry(it)) },
                            )
                        }
                        else -> {
                            WeightHistoryDetailList(
                                historyDetails = state.historyItems.filterIsInstance<ScaleEntry>(),
                                itemsOpened = state.itemsOpened,
                                onItemsOpen = {
                                    handleIntent(HistoryDetailIntent.SetItemsOpened(it))
                                },
                                onItemDelete = {
                                    handleIntent(HistoryDetailIntent.DeleteEntry(it))
                                },
                                // MOB-1173: branch by source — manual opens the full value+note
                                // editor, device-synced falls back to note-only (values read-only).
                                onEditEntry = { handleIntent(HistoryDetailIntent.EditWeightEntry(it)) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.weightEditEntry?.let { entry ->
        WeightEditModal(
            entry = entry,
            onSave = { updated ->
                handleIntent(HistoryDetailIntent.SaveWeightEdit(original = entry, updated = updated))
            },
            onDismiss = { handleIntent(HistoryDetailIntent.DismissWeightEditor) },
        )
    }

    state.bpEditEntry?.let { entry ->
        BpEditModal(
            entry = entry,
            onSave = { updated ->
                handleIntent(HistoryDetailIntent.SaveBpEdit(original = entry, updated = updated))
            },
            onDismiss = { handleIntent(HistoryDetailIntent.DismissBpEditor) },
        )
    }

    state.babyEditEntry?.let { entry ->
        BabyEditModal(
            entry = entry,
            onSave = { weightDecigrams, lengthMm, note, timestamp ->
                handleIntent(
                    HistoryDetailIntent.SaveBabyEdit(
                        entry = entry,
                        weightDecigrams = weightDecigrams,
                        lengthMillimeters = lengthMm,
                        note = note,
                        timestamp = timestamp,
                    ),
                )
            },
            onDismiss = { handleIntent(HistoryDetailIntent.DismissBabyEditor) },
        )
    }
}

/**
 * Full baby edit bottom-sheet (weight/length/notes/date) — opened from the baby history
 * detail. Hosts the shared baby [BabyEntrySection] reusable inputs in a padded, scrollable
 * column (same inputs as manual entry), with a filled SAVE button. Seeds the form with the
 * entry's current values; SAVE converts the inputs back to decigrams/mm and emits the
 * timestamp so the row updates in place (or moves if the date changed).
 */
@Composable
private fun BabyEditModal(
    entry: BabyEntry,
    onSave: (weightDecigrams: Int?, lengthMm: Int?, note: String?, timestamp: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Device-synced baby readings are note-only (values from the scale stay read-only); manual
    // readings are fully editable. (MOB-1173)
    val isManual = entry.source == EntrySource.MANUAL.value
    val form = remember(entry.entry.id) { seededBabyEntryForm(entry) }
    val controls = form.forms.baby.controls
    AppBottomSheet(
        title = "",
        onDismiss = onDismiss,
        // Use the manual entry screen's body color so the (primaryBackground) AppInputs
        // contrast against the sheet and read as form fields — otherwise they're the same
        // color as the sheet and look invisible/scattered.
        containerColor = MeTheme.colorScheme.secondaryBackground,
    ) {
        // Mirror the manual entry screen's container so the AppInputs are padded and scrollable
        // (the raw sheet content slot has no padding → fields render edge-to-edge / scattered).
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeTheme.spacing.sm)
                .padding(top = MeTheme.spacing.md)
                .dismissKeyboardOnTap(),
            verticalArrangement = Arrangement.Top,
        ) {
            // MOB-1223 is scoped to Manual Entry; the History-detail baby edit form stays lb/oz-only
            // (its existing behaviour) — hence the fixed LB_OZ layout + conversion below.
            BabyEntrySection(controls = controls, weightUnit = WeightUnit.LB_OZ, onImeAction = {},enabled = isManual)
            Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppButton(
                    enabled = form.isValid,
                    label = EntryScreenStrings.SaveButton,
                    size = ButtonSize.Large,
                    type = ButtonType.PrimaryFilled,
                    onClick = {
                        val lbs = controls.weight.value.toIntOrNull() ?: 0
                        // oz uses the adult BODY_COMP input: raw digits with an implicit 1-place
                        // decimal ("45" → 4.5), so divide by 10 to recover real ounces. (MOB-1223)
                        val oz = controls.weightOz.value.toDoubleOrNull()?.div(10.0) ?: 0.0
                        // length is BODY_COMP raw digits with an implicit 1-place decimal
                        // ("205" → 20.5), so divide by 10 — same as ounces. (MOB-1223)
                        val inches = controls.length.value.toDoubleOrNull()?.div(10.0)
                        val weightDecigrams =
                            if (lbs > 0 || oz > 0) ConversionTools.convertLbOzToDecigrams(lbs, oz) else null
                        val lengthMm =
                            if (inches != null && inches > 0) ConversionTools.convertInchesToMm(inches) else null
                        onSave(
                            weightDecigrams,
                            lengthMm,
                            controls.notes.value.ifBlank { null },
                            DateTimeConverter.timestampToIso(controls.dateTime.value.getTimestamp()),
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3l))
        }
    }
}

/** Builds a baby entry form pre-filled from an existing [entry] for editing. */
private fun seededBabyEntryForm(entry: BabyEntry): MultiFormGroup<BabyEntryForm> {
    // Edit form stays lb/oz-only (MOB-1223 scoped to Manual Entry); seed accordingly.
    val form = MultiFormGroup.create(forms = BabyEntryForm.create(WeightUnit.LB_OZ))
    val controls = form.forms.baby.controls
    entry.babyWeightDecigrams?.takeIf { it > 0 }?.let {
        val (lb, oz) = ConversionTools.convertDecigramsToLbOz(it)
        controls.weight.setValue(lb.toString())
        // oz field is BODY_COMP (implicit 1-decimal): seed the raw digit string, e.g. 4.5 → "45".
        controls.weightOz.setValue(Math.round(oz * 10).toString())
    }
    entry.babyLengthMillimeters?.takeIf { it > 0 }?.let {
        // length field is BODY_COMP (implicit 1-decimal): seed the raw digit string, 20.5 → "205".
        controls.length.setValue(Math.round(ConversionTools.convertMmToInches(it) * 10).toString())
    }
    entry.entryNote?.let { controls.notes.setValue(it) }
    val millis = DateTimeConverter.isoToTimestamp(entry.entry.entryTimestamp)
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    controls.dateTime.setValue(
        DateTimeValue.DateTime(
            millis = millis,
            hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            minute = calendar.get(java.util.Calendar.MINUTE),
        ),
    )
    return form
}

/** Trims a trailing ".0" so whole values seed as "8" not "8.0" but keeps "14.9". */
private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/**
 * Full weight edit bottom sheet (MOB-1173) for a MANUAL reading. Weight value, note (280-char),
 * date/time and the general body metrics (BMI / body fat / muscle mass / body water) are editable,
 * all seeded from [entry]. R4 scale metrics (heart rate, bone mass, …) aren't shown here — they're
 * carried over unchanged on save. SAVE rebuilds the reading through the shared manual-entry
 * conversion (so units are handled identically to a fresh manual entry) and hands the ViewModel a
 * ready-to-persist copy to persist as an in-place edit (operationType=edit).
 */
@Suppress("LongMethod")
@Composable
private fun WeightEditModal(
    entry: ScaleEntry,
    onSave: (updated: ScaleEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val weightUnit = entry.entry.unit
    // Device-synced readings are note-only: the values came from the device and stay read-only.
    // Manual readings are fully editable. (MOB-1173)
    val isManual = entry.scale.scaleEntry.source == EntrySource.MANUAL.value
    val form = remember(entry.entry.id) { seededWeightEntryForm(entry) }
    val controls = form.forms.weightDateTime.controls
    AppBottomSheet(
        title = "",
        onDismiss = onDismiss,
        containerColor = MeTheme.colorScheme.secondaryBackground,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeTheme.spacing.sm)
                .padding(top = MeTheme.spacing.md)
                .dismissKeyboardOnTap(),
            verticalArrangement = Arrangement.Top,
        ) {
            AppInput(
                formControl = controls.weight,
                label = EntryScreenStrings.WEIGHT_LABEL,
                trailingText = weightUnit.label,
                type = AppInputType.BODY_COMP,
                imeAction = ImeAction.Next,
                onImeAction = {},
                maxLength = 4,
                enabled = isManual,
                modifier = Modifier.fillMaxWidth(),
            )
            // Note is always editable — for both manual and device-synced readings.
            AppTextArea(
                formControl = controls.notes,
                label = EntryScreenStrings.NOTES_LABEL,
                maxLength = EntryScreenStrings.NOTES_MAX_LENGTH,
                showCharacterCounter = true,
            )
            DateTimeInput(
                formControl = controls.dateTime,
                mode = DateTimeInputMode.DateTime,
                label = EntryScreenStrings.DATE_LABEL,
                maxValue = null,
                enabled = isManual,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
            ExpandableMetricsCard(
                title = EntryScreenStrings.METRICS_SECTION_TITLE,
                subheading = EntryScreenStrings.METRICS_SECTION_SUBHEADING,
                generalMetrics = form.forms.generalMetrics.controls,
                r4ScaleMetrics = null,
                expandedInitially = false,
                onImeAction = {},
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
                enabled = isManual,
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppButton(
                    enabled = form.isValid,
                    label = EntryScreenStrings.SaveButton,
                    size = ButtonSize.Large,
                    type = ButtonType.PrimaryFilled,
                    onClick = {
                        val rebuilt = form.forms.toScaleEntry(weightUnit, entry.entry.accountId)
                        // Keep the original row's identity (id, serverTimestamp, device fields) so the
                        // edit resolves IN PLACE via operationType=edit; apply the edited
                        // timestamp/unit and the edited weight + general metrics + note from the form.
                        // R4 metrics aren't edited in this sheet — carry the originals over untouched.
                        val updated = entry.copy(
                            entry = entry.entry.copy(
                                entryTimestamp = rebuilt.entry.entryTimestamp,
                                unit = weightUnit,
                            ),
                            scale = entry.scale.copy(
                                scaleEntry = rebuilt.scale.scaleEntry.copy(
                                    id = entry.scale.scaleEntry.id,
                                    source = entry.scale.scaleEntry.source,
                                ),
                                scaleEntryMetric = entry.scale.scaleEntryMetric,
                            ),
                        )
                        onSave(updated)
                    },
                )
            }
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3l))
        }
    }
}

/** Builds a weight entry form pre-filled from an existing [entry] for editing (MOB-1173). */
private fun seededWeightEntryForm(entry: ScaleEntry): MultiFormGroup<EntryForm> {
    val form = MultiFormGroup.create(
        forms = EntryForm.create(
            includeR4ScaleMetrics = false,
            weightUnit = entry.entry.unit,
            height = null,
            scaleEntry = entry,
        ),
    )
    val controls = form.forms.weightDateTime.controls
    entry.scale.scaleEntry.note?.let { controls.notes.setValue(it) }
    // Device-synced readings store 0.0 for body-composition metrics the device didn't measure; the
    // seeder turns that into "0", which the decimal field renders as "0.0". Show an empty field for
    // an unavailable (zero) metric instead of a misleading "0.0". (MOB-1173)
    val metrics = form.forms.generalMetrics.controls
    listOf(metrics.bodyMassIndex, metrics.bodyFat, metrics.muscleMass, metrics.bodyWater).forEach { control ->
        if (control.value == "0") control.setValue("")
    }
    val millis = DateTimeConverter.isoToTimestamp(entry.entry.entryTimestamp)
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    controls.dateTime.setValue(
        DateTimeValue.DateTime(
            millis = millis,
            hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            minute = calendar.get(java.util.Calendar.MINUTE),
        ),
    )
    return form
}

/**
 * BP edit bottom sheet (MOB-1173). Systolic / diastolic / pulse + note + date, all seeded from
 * [entry]. For a MANUAL reading everything is editable; for a device-synced reading the values +
 * date are disabled and only the note is editable. SAVE hands the ViewModel an in-place-edited copy
 * (same row identity), which is pushed via operationType=edit.
 */
@Composable
private fun BpEditModal(
    entry: BpmEntry,
    onSave: (updated: BpmEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val isManual = entry.source == EntrySource.MANUAL.value
    val form = remember(entry.entry.id) { seededBpEntryForm(entry) }
    val controls = form.forms.bloodPressure.controls
    AppBottomSheet(
        title = "",
        onDismiss = onDismiss,
        containerColor = MeTheme.colorScheme.secondaryBackground,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeTheme.spacing.sm)
                .padding(top = MeTheme.spacing.md)
                .dismissKeyboardOnTap(),
            verticalArrangement = Arrangement.Top,
        ) {
            BloodPressureSection(controls = controls, onImeAction = {}, enabled = isManual)
            Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppButton(
                    enabled = form.isValid,
                    label = EntryScreenStrings.SaveButton,
                    size = ButtonSize.Large,
                    type = ButtonType.PrimaryFilled,
                    onClick = {
                        val sys = controls.systolic.value.toIntOrNull() ?: entry.systolic
                        val dia = controls.diastolic.value.toIntOrNull() ?: entry.diastolic
                        val pul = controls.pulse.value.toIntOrNull() ?: entry.pulse
                        val newTimestamp = DateTimeConverter.timestampToIso(controls.dateTime.value.getTimestamp())
                        // Keep the original row identity (id, serverTimestamp, device fields, source)
                        // so the edit resolves in place; apply the edited values/note/timestamp.
                        val updated = entry.copy(
                            entry = entry.entry.copy(entryTimestamp = newTimestamp),
                            bpmEntry = entry.bpmEntry.copy(
                                systolic = sys,
                                diastolic = dia,
                                pulse = pul,
                                meanArterial = ((sys + 2 * dia) / 3.0).roundToInt().toString(),
                                note = controls.notes.value.ifBlank { null },
                            ),
                        )
                        onSave(updated)
                    },
                )
            }
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3l))
        }
    }
}

/** Builds a BP entry form pre-filled from an existing [entry] for editing (MOB-1173). */
private fun seededBpEntryForm(entry: BpmEntry): MultiFormGroup<BloodPressureEntryForm> {
    val form = MultiFormGroup.create(forms = BloodPressureEntryForm.create())
    val controls = form.forms.bloodPressure.controls
    controls.systolic.setValue(entry.systolic.toString())
    controls.diastolic.setValue(entry.diastolic.toString())
    controls.pulse.setValue(entry.pulse.toString())
    entry.note?.let { controls.notes.setValue(it) }
    val millis = DateTimeConverter.isoToTimestamp(entry.entry.entryTimestamp)
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    controls.dateTime.setValue(
        DateTimeValue.DateTime(
            millis = millis,
            hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            minute = calendar.get(java.util.Calendar.MINUTE),
        ),
    )
    return form
}

@PreviewTheme
@Composable
fun HistoryDetailScreenPreview() {
    MeAppTheme {
        val sampleItems =
            listOf(
                ScaleEntry(
                    entry = EntryEntity(
                        id = 478,
                        accountId = "4SWOWDAP9t2gS50MFp9HQS",
                        entryTimestamp = "2025-06-19T06:30:00.000Z",
                        serverTimestamp = "2025-06-19T10:29:13.914Z",
                        opTimestamp = null,
                        operationType = "create",
                        deviceType = "scale",
                        deviceId = "manual",
                        attempts = 0,
                        unit = WeightUnit.LB,
                        isSynced = true,
                    ),
                    scale = ScaleEntryWithMetrics(
                        scaleEntry = BodyScaleEntryEntity(
                            id = 478,
                            weight = 50.0,
                            bodyFat = 0.0,
                            muscleMass = 0.0,
                            water = 0.0,
                            bmi = 0.0,
                            source = "manual",
                        ),
                        scaleEntryMetric = BodyScaleEntryMetricEntity(
                            id = 478,
                            bmr = 12.0,
                            metabolicAge = 0,
                            proteinPercent = 0.0,
                            pulse = 0,
                            skeletalMusclePercent = 0.0,
                            subcutaneousFatPercent = 0.0,
                            visceralFatLevel = 12.0,
                            boneMass = 0.0,
                            impedance = 0,
                        ),
                    ),
                ),
                ScaleEntry(
                    entry = EntryEntity(
                        id = 479,
                        accountId = "4SWOWDAP9t2gS50MFp9HQS",
                        entryTimestamp = "2025-06-20T06:30:00.000Z",
                        serverTimestamp = "2025-06-20T10:29:13.914Z",
                        opTimestamp = null,
                        operationType = "create",
                        deviceType = "scale",
                        deviceId = "manual",
                        attempts = 0,
                        unit = WeightUnit.KG,
                        isSynced = true,
                    ),
                    scale = ScaleEntryWithMetrics(
                        scaleEntry = BodyScaleEntryEntity(
                            id = 479,
                            weight = 70.0,
                            bodyFat = 0.0,
                            muscleMass = 0.0,
                            water = 0.0,
                            bmi = 0.0,
                            source = "manual",
                        ),
                        scaleEntryMetric = BodyScaleEntryMetricEntity(
                            id = 479,
                            bmr = 12.0,
                            metabolicAge = 0,
                            proteinPercent = 0.0,
                            pulse = 0,
                            skeletalMusclePercent = 0.0,
                            subcutaneousFatPercent = 0.0,
                            visceralFatLevel = 12.0,
                            boneMass = 0.0,
                            impedance = 0,
                        ),
                    ),
                ),
            )
        HistoryDetailScreenContent(
            state =
                HistoryDetailState(
                    month = "Dec 2022",
                    historyItems = sampleItems.toImmutableList(),
                ),
            isRefreshing = false,
            onRefresh = {},
            handleIntent = {},
        )
    }
}
