package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Mapping between domain [Entry] types and the unified `/v3/entries/` DTOs.
 * Single source of truth for field mapping; reused by the read path (MOB-380).
 */

/**
 * Physiological sanity bounds applied before a reading is written to the server.
 * These mirror the manual-entry form limits (`AppValidatorConfig`) but are duplicated
 * here so the domain mapper stays independent of the features layer. A reading outside
 * these bounds (or a 0/garbage sensor value) is dropped rather than POSTed as a real
 * entry — in an atomic batch one bad reading would otherwise fail the whole batch.
 */
private const val SYSTOLIC_MIN = 60
private const val SYSTOLIC_MAX = 250
private const val DIASTOLIC_MIN = 40
private const val DIASTOLIC_MAX = 150
private const val PULSE_MIN = 30
private const val PULSE_MAX = 250

/** True when sys/dia/pulse are all within physiological range and safe to write. */
private fun BpmEntry.hasValidReading(): Boolean =
    systolic in SYSTOLIC_MIN..SYSTOLIC_MAX &&
        diastolic in DIASTOLIC_MIN..DIASTOLIC_MAX &&
        pulse in PULSE_MIN..PULSE_MAX

/** Builds the unified request for a weight entry (reuses the legacy field math). */
fun ScaleEntry.toUnifiedRequest(): UnifiedEntryRequest {
    val api = toScaleApiEntry()
    return UnifiedEntryRequest(
        category = EntryCategory.WEIGHT.value,
        operationType = api.operationType,
        entryTimestamp = api.entryTimestamp,
        weight = api.weight,
        bodyFat = api.bodyFat,
        muscleMass = api.muscleMass,
        water = api.water,
        bmi = api.bmi,
        boneMass = api.boneMass,
        impedance = api.impedance,
        unit = api.unit,
        pulse = api.pulse,
        source = api.source,
    )
}

/** Builds the unified request for a BP entry. Manual entry is the only BP write path today. */
fun BpmEntry.toUnifiedRequest(): UnifiedEntryRequest = UnifiedEntryRequest(
    category = EntryCategory.BP.value,
    operationType = entry.operationType.lowercase(),
    entryTimestamp = entry.entryTimestamp,
    systolic = systolic,
    diastolic = diastolic,
    pulse = pulse,
    note = note,
    source = EntrySource.MANUAL.value,
)

/**
 * Builds the unified request for a baby entry (§2.16 / MOB-381). Weight and length are
 * distinct `entryType`s, so each [BabyEntry] carries exactly one measure — the value that
 * doesn't match its [entryType] is left null.
 */
fun BabyEntry.toUnifiedRequest(): UnifiedEntryRequest {
    val type = BabyEntryType.fromValue(entryType)
    return UnifiedEntryRequest(
        category = EntryCategory.BABY.value,
        operationType = entry.operationType.lowercase(),
        entryTimestamp = entry.entryTimestamp,
        babyId = babyId,
        entryType = type.value,
        babyWeightDecigrams = if (type == BabyEntryType.WEIGHT) babyWeightDecigrams else null,
        babyLengthMillimeters = if (type == BabyEntryType.MEASURE_LENGTH) babyLengthMillimeters else null,
        entryNote = entryNote,
        source = babyEntry.source ?: EntrySource.MANUAL.value,
    )
}

/** True when the baby entry carries a positive value for its [entryType]. */
private fun BabyEntry.hasValidReading(): Boolean = when (BabyEntryType.fromValue(entryType)) {
    BabyEntryType.WEIGHT -> (babyWeightDecigrams ?: 0) > 0
    BabyEntryType.MEASURE_LENGTH -> (babyLengthMillimeters ?: 0) > 0
}

/**
 * Maps a domain [Entry] to a [UnifiedEntryRequest], or null when the reading is
 * invalid/garbage (dropped rather than failing the atomic batch).
 */
fun Entry.toUnifiedRequestOrNull(): UnifiedEntryRequest? = when (this) {
    // Drop a 0/garbage weight rather than writing it as a real reading (mirrors the
    // read-path guard that refuses to persist a 0-weight entry).
    is ScaleEntry -> toUnifiedRequest().takeIf { (it.weight ?: 0) > 0 }
    is BpmEntry -> if (hasValidReading()) toUnifiedRequest() else null
    is BabyEntry -> if (hasValidReading()) toUnifiedRequest() else null
}

/**
 * Maps a server [UnifiedEntry] back to a domain [Entry] for local persistence,
 * or null for unknown/unsupported categories.
 */
fun UnifiedEntry.toDomainEntry(accountId: String): Entry? =
    when (EntryCategory.fromValue(category)) {
        // Weight is required for the weight category; drop a malformed entry rather
        // than persisting it as a 0-weight reading.
        EntryCategory.WEIGHT -> weight?.let { ScaleEntry.fromScaleApiEntry(toScaleApiEntry(), accountId = accountId) }
        EntryCategory.BP -> toBpmEntry(accountId)
        else -> null
    }

/** Adapts the weight fields of a [UnifiedEntry] to the existing [ScaleApiEntry] shape. */
private fun UnifiedEntry.toScaleApiEntry(): ScaleApiEntry = ScaleApiEntry(
    operationType = operationType ?: EntryOperationType.CREATE.value,
    entryTimestamp = entryTimestamp,
    serverTimestamp = serverTimestamp,
    weight = weight ?: 0,
    bodyFat = bodyFat,
    muscleMass = muscleMass,
    boneMass = boneMass,
    water = water,
    bmi = bmi,
    source = source,
    unit = unit,
    impedance = impedance,
    pulse = pulse,
    visceralFatLevel = null,
    subcutaneousFatPercent = null,
    proteinPercent = null,
    skeletalMusclePercent = null,
    bmr = null,
    metabolicAge = null,
)

private fun UnifiedEntry.toBpmEntry(accountId: String): BpmEntry {
    val sys = systolic ?: 0
    val dia = diastolic ?: 0
    val entryEntity = EntryEntity(
        accountId = accountId,
        entryTimestamp = entryTimestamp,
        serverTimestamp = serverTimestamp,
        opTimestamp = serverTimestamp,
        operationType = operationType ?: EntryOperationType.CREATE.value,
        deviceType = "bpm",
        deviceId = UUID.randomUUID().toString(),
        unit = WeightUnit.LB,
        isSynced = true,
    )
    val bpmEntity = BpmEntryEntity(
        id = 0L,
        systolic = sys,
        diastolic = dia,
        pulse = pulse ?: 0,
        meanArterial = ((sys + 2 * dia) / 3.0).roundToInt().toString(),
        note = note,
    )
    return BpmEntry(entry = entryEntity, bpmEntry = bpmEntity)
}
