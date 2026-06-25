package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
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
 * Builds the §2.16 baby request(s) for this entry. A single local baby row carries BOTH
 * measures (matching the combined history/detail row), but the server models `weight` and
 * `measureLength` as distinct entries (distinct `entryType` + `entryId`) that may share an
 * `entryTimestamp`. So a combined row fans out to up to TWO requests; a measure of 0/null
 * is dropped rather than POSTed as a garbage reading.
 */
fun BabyEntry.toUnifiedRequests(): List<UnifiedEntryRequest> = buildList {
    val weightDg = babyWeightDecigrams
    if (weightDg != null && weightDg > 0) {
        add(babyRequest(BabyEntryType.WEIGHT, weightDecigrams = weightDg))
    }
    val lengthMm = babyLengthMillimeters
    if (lengthMm != null && lengthMm > 0) {
        add(babyRequest(BabyEntryType.MEASURE_LENGTH, lengthMm = lengthMm))
    }
}

private fun BabyEntry.babyRequest(
    type: BabyEntryType,
    weightDecigrams: Int? = null,
    lengthMm: Int? = null,
): UnifiedEntryRequest = UnifiedEntryRequest(
    category = EntryCategory.BABY.value,
    operationType = entry.operationType.lowercase(),
    entryTimestamp = entry.entryTimestamp,
    babyId = babyId,
    // Deterministic idempotency key (§2.16 requires entryId for baby): baby + entryType +
    // timestamp always yields the same id, so a retried sync can't duplicate it server-side
    // and the weight/length halves of one reading get distinct ids.
    entryId = babyEntryId(babyId, type.value, entry.entryTimestamp),
    entryType = type.value,
    babyWeightDecigrams = weightDecigrams,
    babyLengthMillimeters = lengthMm,
    entryNote = entryNote,
    source = babyEntry.source ?: EntrySource.MANUAL.value,
)

/**
 * Stable client idempotency key for a baby entry (§2.16 `entryId`). Derived from the
 * fields that identify the logical reading so retries/edits resolve to the same id.
 */
private fun babyEntryId(babyId: String, entryType: String, entryTimestamp: String): String =
    "${babyId}_${entryType}_$entryTimestamp"

/**
 * Maps a domain [Entry] to the unified request(s) to POST. Weight/BP map to at most one;
 * baby may map to two (weight + measureLength). Garbage/empty readings are dropped so a
 * single bad reading can't fail the atomic batch.
 */
fun Entry.toUnifiedRequests(): List<UnifiedEntryRequest> = when (this) {
    // Drop a 0/garbage weight rather than writing it as a real reading.
    is ScaleEntry -> listOfNotNull(toUnifiedRequest().takeIf { (it.weight ?: 0) > 0 })
    is BpmEntry -> listOfNotNull(if (hasValidReading()) toUnifiedRequest() else null)
    is BabyEntry -> toUnifiedRequests()
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
        EntryCategory.BABY -> toBabyEntry(accountId)
        else -> null
    }

/**
 * Maps a batch of server [UnifiedEntry] rows to domain entries, merging the baby `weight`
 * and `measureLength` rows that share a (babyId, entryTimestamp) back into ONE combined
 * [BabyEntry] — the inverse of the POST split ([toUnifiedRequests]). This is required by the
 * local UNIQUE(accountId, entryTimestamp) index, which allows only one row per timestamp.
 * Weight/BP map 1:1.
 */
fun List<UnifiedEntry>.toDomainEntries(accountId: String): List<Entry> {
    val (baby, others) = partition { EntryCategory.fromValue(it.category) == EntryCategory.BABY }
    val mappedOthers = others.mapNotNull { it.toDomainEntry(accountId) }
    val mergedBaby = baby
        .groupBy { it.babyId to it.entryTimestamp }
        .mapNotNull { (_, group) -> group.toMergedBabyEntry(accountId) }
    return mappedOthers + mergedBaby
}

/** Combines the weight + measureLength server rows of one baby reading into a single [BabyEntry]. */
private fun List<UnifiedEntry>.toMergedBabyEntry(accountId: String): BabyEntry? {
    val first = firstOrNull() ?: return null
    val id = first.babyId ?: return null
    val weightDg = firstNotNullOfOrNull { e ->
        e.babyWeightDecigrams?.takeIf { e.entryType == BabyEntryType.WEIGHT.value && it > 0 }
    }
    val lengthMm = firstNotNullOfOrNull { e ->
        e.babyLengthMillimeters?.takeIf { e.entryType == BabyEntryType.MEASURE_LENGTH.value && it > 0 }
    }
    if (weightDg == null && lengthMm == null) return null

    val entryEntity = EntryEntity(
        accountId = accountId,
        entryTimestamp = first.entryTimestamp,
        serverTimestamp = first.serverTimestamp,
        opTimestamp = first.serverTimestamp,
        operationType = first.operationType ?: EntryOperationType.CREATE.value,
        deviceType = "manual",
        deviceId = first.entryId ?: "",
        unit = WeightUnit.LB,
        isSynced = true,
    )
    val babyEntity = BabyEntryEntity(
        id = 0L,
        babyId = id,
        babyWeightDecigrams = weightDg,
        babyLengthMillimeters = lengthMm,
        entryNote = firstNotNullOfOrNull { it.entryNote },
        entryType = if (weightDg != null) BabyEntryType.WEIGHT.value else BabyEntryType.MEASURE_LENGTH.value,
        source = first.source,
    )
    return BabyEntry(entry = entryEntity, babyEntry = babyEntity)
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

/**
 * Maps a server baby [UnifiedEntry] back to a domain [BabyEntry] (read path for §2.17).
 * Only `weight` and `measureLength` are modeled locally — other baby entryTypes
 * (feeding/sleep/diaper/snapshot) and entries without a usable value return null so
 * they're skipped rather than mis-stored. The raw [entryType] string is matched
 * explicitly (not via [BabyEntryType.fromValue], which defaults unknown values to WEIGHT).
 */
private fun UnifiedEntry.toBabyEntry(accountId: String): BabyEntry? {
    val id = babyId ?: return null
    val type = when (entryType) {
        BabyEntryType.WEIGHT.value -> BabyEntryType.WEIGHT
        BabyEntryType.MEASURE_LENGTH.value -> BabyEntryType.MEASURE_LENGTH
        else -> return null
    }
    val weightDg = if (type == BabyEntryType.WEIGHT) babyWeightDecigrams else null
    val lengthMm = if (type == BabyEntryType.MEASURE_LENGTH) babyLengthMillimeters else null
    if ((weightDg ?: 0) <= 0 && (lengthMm ?: 0) <= 0) return null

    val entryEntity = EntryEntity(
        accountId = accountId,
        entryTimestamp = entryTimestamp,
        serverTimestamp = serverTimestamp,
        opTimestamp = serverTimestamp,
        operationType = operationType ?: EntryOperationType.CREATE.value,
        deviceType = "manual",
        deviceId = entryId ?: "",
        unit = WeightUnit.LB,
        isSynced = true,
    )
    val babyEntity = BabyEntryEntity(
        id = 0L,
        babyId = id,
        babyWeightDecigrams = weightDg,
        babyLengthMillimeters = lengthMm,
        entryNote = entryNote,
        entryType = type.value,
        source = source,
    )
    return BabyEntry(entry = entryEntity, babyEntry = babyEntity)
}
