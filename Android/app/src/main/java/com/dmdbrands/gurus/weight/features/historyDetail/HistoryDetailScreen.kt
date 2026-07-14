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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.features.common.components.AppBottomSheet
import com.dmdbrands.gurus.weight.features.common.components.AppButton
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
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import com.dmdbrands.gurus.weight.features.manualEntry.components.BabyEntrySection
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.BabyEntryForm
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
    // When the last entry in this month/day is deleted, the detail list becomes empty — pop back
    // to the history list instead of leaving the user on a blank detail screen. `hadEntries` gates
    // out the initial empty state (before data loads); `popped` guards against a double-pop if the
    // flow re-emits empty. (MOB-1462)
    var hadEntries by remember { mutableStateOf(false) }
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(state.historyItems, state.isLoading) {
        when {
            state.historyItems.isNotEmpty() -> hadEntries = true
            hadEntries && !state.isLoading && !popped -> {
                popped = true
                backStack.removeLast()
            }
        }
    }
    AppScaffold(
        title = state.month,
        // Month title is centered across the bar to match the Figma WG history-detail header
        // (MOB-1470) — the close icon stays pinned left.
        centerTitle = true,
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
                                onEditEntry = { handleIntent(HistoryDetailIntent.EditEntry(it)) },
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
                                onEditEntry = { handleIntent(HistoryDetailIntent.EditEntry(it)) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.noteEditEntry?.let { entry ->
        NoteEditBottomSheet(
            entry = entry,
            onSave = { note -> handleIntent(HistoryDetailIntent.SaveNote(entry, note)) },
            onDismiss = { handleIntent(HistoryDetailIntent.DismissNoteEditor) },
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
            BabyEntrySection(controls = controls, weightUnit = WeightUnit.LB_OZ, onImeAction = {})
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
 * Bottom-sheet modal for adding/editing an entry's note (MOB-438). Seeds the field with
 * the entry's current note and enforces the shared 280-char limit + counter.
 */
@Composable
private fun NoteEditBottomSheet(
    entry: Entry,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val noteControl = remember(entry.entry.id) {
        FormControl.create(entry.noteText().orEmpty(), emptyList())
    }
    AppBottomSheet(
        title = EntryScreenStrings.NOTES_LABEL,
        onDismiss = onDismiss,
    ) {
        AppTextArea(
            formControl = noteControl,
            label = EntryScreenStrings.NOTES_LABEL,
            maxLength = EntryScreenStrings.NOTES_MAX_LENGTH,
            showCharacterCounter = true,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.md))
        AppButton(
            label = HistoryDetailScreenStrings.SaveButton,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSave(noteControl.value) },
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
    }
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
