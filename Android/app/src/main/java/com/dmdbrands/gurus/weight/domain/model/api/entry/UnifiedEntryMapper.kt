package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import java.util.UUID

/**
 * Mapping between domain [Entry] types and the unified `/v3/entries/` DTOs.
 * Single source of truth for field mapping; reused by the read path (MOB-380).
 */

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
 * Maps a domain [Entry] to a [UnifiedEntryRequest], or null for categories not
 * wired for write yet (baby — Android 3 / MOB-381).
 */
fun Entry.toUnifiedRequestOrNull(): UnifiedEntryRequest? = when (this) {
    is ScaleEntry -> toUnifiedRequest()
    is BpmEntry -> toUnifiedRequest()
    is BabyEntry -> null
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
        meanArterial = ((sys + 2 * dia) / 3).toString(),
        note = note,
    )
    return BpmEntry(entry = entryEntity, bpmEntry = bpmEntity)
}
