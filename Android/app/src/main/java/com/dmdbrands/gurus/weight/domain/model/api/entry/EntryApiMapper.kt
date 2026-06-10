package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import java.util.UUID

/** API category constants (mirrors MOB-379's EntryCategory enum values). */
internal object EntryApiCategory {
    const val WEIGHT = "weight"
    const val BP = "bp"
}

/**
 * Maps a server [EntryApiModel] to a domain [Entry] for local persistence,
 * or null for unknown / unsupported categories.
 *
 * A missing `weight` on a weight-category entry returns null rather than
 * persisting a 0-weight reading.
 */
fun EntryApiModel.toDomainEntry(accountId: String): Entry? =
    when (category) {
        EntryApiCategory.WEIGHT -> weight?.let { toScaleEntry(accountId) }
        EntryApiCategory.BP -> toBpmEntry(accountId)
        else -> null
    }

/** Maps a list of API models, silently dropping unsupported categories. */
fun List<EntryApiModel>.toDomainEntries(accountId: String): List<Entry> =
    mapNotNull { it.toDomainEntry(accountId) }

private fun EntryApiModel.toScaleEntry(accountId: String): ScaleEntry {
    val scaleApiEntry = ScaleApiEntry(
        operationType = operationType ?: "create",
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
    return ScaleEntry.fromScaleApiEntry(scaleApiEntry, accountId = accountId)
}

private fun EntryApiModel.toBpmEntry(accountId: String): BpmEntry {
    val sys = systolic ?: 0
    val dia = diastolic ?: 0
    val entryEntity = EntryEntity(
        accountId = accountId,
        entryTimestamp = entryTimestamp,
        serverTimestamp = serverTimestamp,
        opTimestamp = serverTimestamp,
        operationType = operationType ?: "create",
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
