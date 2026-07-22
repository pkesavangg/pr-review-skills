package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UnifiedEntryMapperTest {

    companion object {
        private const val ACCOUNT_ID = "account-123"
        private const val TIMESTAMP = "2024-05-01T10:00:00.000Z"
    }

    private fun scaleEntry() = ScaleEntry(
        entry = EntryEntity(
            accountId = ACCOUNT_ID,
            entryTimestamp = TIMESTAMP,
            operationType = "CREATE",
            deviceType = "scale",
            deviceId = "d1",
            unit = WeightUnit.LB,
        ),
        scale = ScaleEntryWithMetrics(
            scaleEntry = BodyScaleEntryEntity(
                id = 0L, weight = 750.0, bodyFat = null, muscleMass = null, water = null, bmi = null, source = "manual",
            ),
            scaleEntryMetric = null,
        ),
    )

    private fun bpmEntry(op: String = "CREATE") = BpmEntry(
        entry = EntryEntity(
            accountId = ACCOUNT_ID,
            entryTimestamp = TIMESTAMP,
            operationType = op,
            deviceType = "bpm",
            deviceId = "d2",
        ),
        bpmEntry = BpmEntryEntity(
            id = 0L,
            systolic = 120,
            diastolic = 80,
            pulse = 72,
            meanArterial = "93",
            note = "after run",
        ),
    )

    // ── enums ──────────────────────────────────────────────────────────────────

    @Test
    fun `EntryCategory fromValue resolves and falls back to null`() {
        assertThat(EntryCategory.fromValue("weight")).isEqualTo(EntryCategory.WEIGHT)
        assertThat(EntryCategory.fromValue("bp")).isEqualTo(EntryCategory.BP)
        assertThat(EntryCategory.fromValue("baby")).isEqualTo(EntryCategory.BABY)
        assertThat(EntryCategory.fromValue("nope")).isNull()
    }

    @Test
    fun `EntryOperationType fromValue is case-insensitive and defaults to create`() {
        assertThat(EntryOperationType.fromValue("DELETE")).isEqualTo(EntryOperationType.DELETE)
        assertThat(EntryOperationType.fromValue(null)).isEqualTo(EntryOperationType.CREATE)
        assertThat(EntryOperationType.fromValue("garbage")).isEqualTo(EntryOperationType.CREATE)
    }

    // ── ScaleEntry → request ─────────────────────────────────────────────────────

    @Test
    fun `weight entry maps to weight category with lowercase op`() {
        val req = scaleEntry().toUnifiedRequest()

        assertThat(req.category).isEqualTo(EntryCategory.WEIGHT.value)
        assertThat(req.operationType).isEqualTo("create")
        assertThat(req.entryTimestamp).isEqualTo(TIMESTAMP)
        assertThat(req.unit).isEqualTo(WeightUnit.LB.value)
        assertThat(req.weight).isNotNull()
        // BP-only fields stay null on a weight request.
        assertThat(req.systolic).isNull()
        assertThat(req.diastolic).isNull()
    }

    // ── 12-metric R4 round-trip (MOB-1496 / PR #2291) ────────────────────────────
    // Regression guard: the 6 advanced R4 metrics (visceralFatLevel, subcutaneousFatPercent,
    // proteinPercent, skeletalMusclePercent, bmr, metabolicAge) were previously hardcoded to null
    // in the unified /v3/entries DTOs, so a 12-metric weight reading lost them on sync. This proves
    // all 12 metrics survive the write mapper (toUnifiedRequest) AND the read mapper (toDomainEntry).

    /** A ScaleApiEntry carrying all 12 metrics (6 base body-comp + 6 advanced R4), unit = lb. */
    private fun twelveMetricApi() = ScaleApiEntry(
        operationType = "create",
        entryTimestamp = TIMESTAMP,
        serverTimestamp = TIMESTAMP,
        weight = 750,
        bodyFat = 200,
        muscleMass = 550,
        boneMass = 30,
        water = 500,
        bmi = 240,
        source = "manual",
        unit = WeightUnit.LB.value,
        impedance = 480,
        pulse = 72,
        visceralFatLevel = 120,
        subcutaneousFatPercent = 150,
        proteinPercent = 180,
        skeletalMusclePercent = 450,
        bmr = 1_500,
        metabolicAge = 30,
    )

    @Test
    fun `12-metric weight reading round-trips through the mappers without dropping R4 fields`() {
        val original = twelveMetricApi()
        val scaleEntry = ScaleEntry.fromScaleApiEntry(original, accountId = ACCOUNT_ID)

        // WRITE mapper: ScaleEntry → UnifiedEntryRequest must carry every metric.
        val req = scaleEntry.toUnifiedRequest()
        assertThat(req.category).isEqualTo(EntryCategory.WEIGHT.value)
        // 6 base body-composition metrics.
        assertThat(req.weight).isEqualTo(750)
        assertThat(req.bodyFat).isEqualTo(200)
        assertThat(req.muscleMass).isEqualTo(550)
        assertThat(req.water).isEqualTo(500)
        assertThat(req.bmi).isEqualTo(240)
        assertThat(req.boneMass).isEqualTo(30)
        // 6 advanced R4 metrics — the fields the regression dropped.
        assertThat(req.visceralFatLevel).isEqualTo(120)
        assertThat(req.subcutaneousFatPercent).isEqualTo(150)
        assertThat(req.proteinPercent).isEqualTo(180)
        assertThat(req.skeletalMusclePercent).isEqualTo(450)
        assertThat(req.bmr).isEqualTo(1_500)
        assertThat(req.metabolicAge).isEqualTo(30)

        // READ mapper: the server echoes the same 12 values back; map to domain and re-extract.
        val echoed = UnifiedEntry(
            category = req.category,
            operationType = req.operationType,
            entryTimestamp = req.entryTimestamp,
            serverTimestamp = TIMESTAMP,
            weight = req.weight,
            bodyFat = req.bodyFat,
            muscleMass = req.muscleMass,
            water = req.water,
            bmi = req.bmi,
            boneMass = req.boneMass,
            impedance = req.impedance,
            unit = req.unit,
            pulse = req.pulse,
            source = req.source,
            visceralFatLevel = req.visceralFatLevel,
            subcutaneousFatPercent = req.subcutaneousFatPercent,
            proteinPercent = req.proteinPercent,
            skeletalMusclePercent = req.skeletalMusclePercent,
            bmr = req.bmr,
            metabolicAge = req.metabolicAge,
        )

        val roundTripped = (echoed.toDomainEntry(ACCOUNT_ID) as ScaleEntry).toScaleApiEntry()

        // All 12 metrics survive the full write→read round-trip unchanged.
        assertThat(roundTripped.weight).isEqualTo(original.weight)
        assertThat(roundTripped.bodyFat).isEqualTo(original.bodyFat)
        assertThat(roundTripped.muscleMass).isEqualTo(original.muscleMass)
        assertThat(roundTripped.water).isEqualTo(original.water)
        assertThat(roundTripped.bmi).isEqualTo(original.bmi)
        assertThat(roundTripped.boneMass).isEqualTo(original.boneMass)
        assertThat(roundTripped.visceralFatLevel).isEqualTo(original.visceralFatLevel)
        assertThat(roundTripped.subcutaneousFatPercent).isEqualTo(original.subcutaneousFatPercent)
        assertThat(roundTripped.proteinPercent).isEqualTo(original.proteinPercent)
        assertThat(roundTripped.skeletalMusclePercent).isEqualTo(original.skeletalMusclePercent)
        assertThat(roundTripped.bmr).isEqualTo(original.bmr)
        assertThat(roundTripped.metabolicAge).isEqualTo(original.metabolicAge)
    }

    // ── BpmEntry → request ───────────────────────────────────────────────────────

    @Test
    fun `bp entry maps to bp category with required fields and manual source`() {
        val req = bpmEntry().toUnifiedRequest()

        assertThat(req.category).isEqualTo(EntryCategory.BP.value)
        assertThat(req.operationType).isEqualTo("create")
        assertThat(req.systolic).isEqualTo(120)
        assertThat(req.diastolic).isEqualTo(80)
        assertThat(req.pulse).isEqualTo(72)
        assertThat(req.source).isEqualTo(EntrySource.MANUAL.value)
        assertThat(req.note).isEqualTo("after run")
        // weight stays null on a bp request.
        assertThat(req.weight).isNull()
    }

    @Test
    fun `delete op is lowercased`() {
        assertThat(bpmEntry(op = "DELETE").toUnifiedRequest().operationType).isEqualTo("delete")
    }

    // ── BabyEntry → request (MOB-381 / §2.16) ────────────────────────────────────

    private fun babyEntry(
        type: BabyEntryType,
        weightDecigrams: Int? = null,
        lengthMm: Int? = null,
    ) = BabyEntry(
        entry = EntryEntity(
            accountId = ACCOUNT_ID,
            entryTimestamp = TIMESTAMP,
            operationType = "CREATE",
            deviceType = "manual",
            deviceId = "",
        ),
        babyEntry = BabyEntryEntity(
            id = 0L,
            babyId = "baby-1",
            babyWeightDecigrams = weightDecigrams,
            babyLengthMillimeters = lengthMm,
            entryNote = "after bath",
            entryType = if (weightDecigrams != null) BabyEntryType.WEIGHT.value else BabyEntryType.MEASURE_LENGTH.value,
            source = EntrySource.MANUAL.value,
        ),
    )

    @Test
    fun `baby weight entry maps to baby category with weight entryType`() {
        val req = babyEntry(BabyEntryType.WEIGHT, weightDecigrams = 45_200).toUnifiedRequests().single()

        assertThat(req.category).isEqualTo(EntryCategory.BABY.value)
        assertThat(req.operationType).isEqualTo("create")
        assertThat(req.babyId).isEqualTo("baby-1")
        assertThat(req.entryType).isEqualTo(BabyEntryType.WEIGHT.value)
        assertThat(req.babyWeightDecigrams).isEqualTo(45_200)
        assertThat(req.babyLengthMillimeters).isNull()
        assertThat(req.entryNote).isEqualTo("after bath")
        assertThat(req.source).isEqualTo(EntrySource.MANUAL.value)
    }

    @Test
    fun `baby length entry maps to measureLength entryType`() {
        val req = babyEntry(BabyEntryType.MEASURE_LENGTH, lengthMm = 510).toUnifiedRequests().single()

        assertThat(req.entryType).isEqualTo(BabyEntryType.MEASURE_LENGTH.value)
        assertThat(req.babyLengthMillimeters).isEqualTo(510)
        assertThat(req.babyWeightDecigrams).isNull()
    }

    @Test
    fun `toUnifiedRequests dispatches baby and drops empty readings`() {
        assertThat(babyEntry(BabyEntryType.WEIGHT, weightDecigrams = 45_200).toUnifiedRequests().single().category)
            .isEqualTo("baby")
        assertThat(babyEntry(BabyEntryType.MEASURE_LENGTH, lengthMm = 510).toUnifiedRequests())
            .isNotEmpty()
        // No value for the entryType → dropped, not POSTed as a 0 reading.
        assertThat(babyEntry(BabyEntryType.WEIGHT, weightDecigrams = 0).toUnifiedRequests()).isEmpty()
        assertThat(babyEntry(BabyEntryType.MEASURE_LENGTH, lengthMm = null).toUnifiedRequests()).isEmpty()
    }

    // ── Entry.toUnifiedRequests ──────────────────────────────────────────────────

    @Test
    fun `toUnifiedRequests dispatches weight and bp`() {
        assertThat(scaleEntry().toUnifiedRequests().single().category).isEqualTo("weight")
        assertThat(bpmEntry().toUnifiedRequests().single().category).isEqualTo("bp")
    }

    @Test
    fun `toUnifiedRequests drops a zero-weight reading`() {
        val zeroWeight = scaleEntry().let {
            it.copy(scale = it.scale.copy(scaleEntry = it.scale.scaleEntry.copy(weight = 0.0)))
        }
        assertThat(zeroWeight.toUnifiedRequests()).isEmpty()
    }

    @Test
    fun `toUnifiedRequests drops an out-of-range bp reading`() {
        // systolic 0 / diastolic 0 — a garbage reading must not be POSTed as a real entry.
        val garbage = bpmEntry().let {
            it.copy(bpmEntry = it.bpmEntry.copy(systolic = 0, diastolic = 0, pulse = 0))
        }
        assertThat(garbage.toUnifiedRequests()).isEmpty()
        // A systolic above the hard cap (BP_HARD_MAX = 500) is also rejected.
        val tooHigh = bpmEntry().let { it.copy(bpmEntry = it.bpmEntry.copy(systolic = 600)) }
        assertThat(tooHigh.toUnifiedRequests()).isEmpty()
    }

    // ── UnifiedEntry → domain ────────────────────────────────────────────────────

    @Test
    fun `response weight entry maps back to ScaleEntry`() {
        val unified = UnifiedEntry(
            category = "weight",
            operationType = "create",
            entryTimestamp = TIMESTAMP,
            serverTimestamp = TIMESTAMP,
            weight = 750,
            unit = "lb",
        )

        val domain = unified.toDomainEntry(ACCOUNT_ID)

        assertThat(domain).isInstanceOf(ScaleEntry::class.java)
        assertThat(domain?.entry?.accountId).isEqualTo(ACCOUNT_ID)
    }

    @Test
    fun `response bp entry maps to BpmEntry with computed mean arterial`() {
        val unified = UnifiedEntry(
            category = "bp",
            operationType = "create",
            entryTimestamp = TIMESTAMP,
            serverTimestamp = TIMESTAMP,
            systolic = 120,
            diastolic = 80,
            pulse = 72,
            note = "n",
        )

        val domain = unified.toDomainEntry(ACCOUNT_ID) as? BpmEntry

        assertThat(domain).isNotNull()
        assertThat(domain?.systolic).isEqualTo(120)
        assertThat(domain?.diastolic).isEqualTo(80)
        assertThat(domain?.meanArterial).isEqualTo("93") // (120 + 160) / 3 = 93.33 → 93
    }

    @Test
    fun `mean arterial rounds to nearest rather than truncating`() {
        // (121 + 2*80) / 3 = 93.67 — integer division would truncate to 93.
        val unified = UnifiedEntry(
            category = "bp",
            entryTimestamp = TIMESTAMP,
            serverTimestamp = TIMESTAMP,
            systolic = 121,
            diastolic = 80,
            pulse = 72,
        )

        val domain = unified.toDomainEntry(ACCOUNT_ID) as? BpmEntry

        assertThat(domain?.meanArterial).isEqualTo("94")
    }

    @Test
    fun `response unknown category maps to null`() {
        val unified = UnifiedEntry(category = "nope", entryTimestamp = TIMESTAMP)
        assertThat(unified.toDomainEntry(ACCOUNT_ID)).isNull()
    }

    // ── UnifiedEntry → BabyEntry (read path, §2.17) ──────────────────────────────

    @Test
    fun `response baby weight entry maps to a synced BabyEntry`() {
        val unified = UnifiedEntry(
            category = "baby",
            operationType = "create",
            entryTimestamp = TIMESTAMP,
            serverTimestamp = TIMESTAMP,
            babyId = "baby-1",
            entryId = "baby-1_weight_$TIMESTAMP",
            entryType = "weight",
            babyWeightDecigrams = 45_200,
            entryNote = "after bath",
            source = "manual",
        )

        val domain = unified.toDomainEntry(ACCOUNT_ID) as? BabyEntry

        assertThat(domain).isNotNull()
        assertThat(domain?.babyId).isEqualTo("baby-1")
        assertThat(domain?.entryType).isEqualTo("weight")
        assertThat(domain?.babyWeightDecigrams).isEqualTo(45_200)
        assertThat(domain?.babyLengthMillimeters).isNull()
        assertThat(domain?.entry?.isSynced).isTrue()
        assertThat(domain?.entry?.deviceId).isEqualTo("baby-1_weight_$TIMESTAMP")
    }

    @Test
    fun `response baby measureLength entry maps with length only`() {
        val unified = UnifiedEntry(
            category = "baby",
            entryTimestamp = TIMESTAMP,
            babyId = "baby-1",
            entryType = "measureLength",
            babyLengthMillimeters = 510,
        )

        val domain = unified.toDomainEntry(ACCOUNT_ID) as? BabyEntry

        assertThat(domain?.babyLengthMillimeters).isEqualTo(510)
        assertThat(domain?.babyWeightDecigrams).isNull()
    }

    @Test
    fun `response baby entry of an unmodeled type is skipped`() {
        // feeding/sleep/diaper/snapshot are not modeled locally — must not be mis-stored as weight.
        val unified = UnifiedEntry(
            category = "baby",
            entryTimestamp = TIMESTAMP,
            babyId = "baby-1",
            entryType = "feedingBottle",
        )
        assertThat(unified.toDomainEntry(ACCOUNT_ID)).isNull()
    }

    @Test
    fun `response baby entry without babyId or value maps to null`() {
        // No babyId.
        assertThat(
            UnifiedEntry(category = "baby", entryTimestamp = TIMESTAMP, entryType = "weight", babyWeightDecigrams = 45_200)
                .toDomainEntry(ACCOUNT_ID),
        ).isNull()
        // babyId present but no usable value.
        assertThat(
            UnifiedEntry(category = "baby", entryTimestamp = TIMESTAMP, babyId = "baby-1", entryType = "weight")
                .toDomainEntry(ACCOUNT_ID),
        ).isNull()
    }

    // ── List<UnifiedEntry>.toDomainEntries (read-back merge) ─────────────────────

    @Test
    fun `server weight + measureLength of one reading merge into a single BabyEntry`() {
        // The server keeps them as two entries (distinct entryId, shared timestamp); the local
        // UNIQUE(accountId, entryTimestamp) index needs them merged back into one row.
        val server = listOf(
            UnifiedEntry(
                category = "baby", operationType = "create", entryTimestamp = TIMESTAMP, serverTimestamp = TIMESTAMP,
                babyId = "baby-1", entryId = "baby-1_weight_$TIMESTAMP", entryType = "weight", babyWeightDecigrams = 45_200,
            ),
            UnifiedEntry(
                category = "baby", operationType = "create", entryTimestamp = TIMESTAMP, serverTimestamp = TIMESTAMP,
                babyId = "baby-1", entryId = "baby-1_measureLength_$TIMESTAMP", entryType = "measureLength",
                babyLengthMillimeters = 510,
            ),
        )

        val domain = server.toDomainEntries(ACCOUNT_ID)

        assertThat(domain).hasSize(1)
        val baby = domain.single() as BabyEntry
        assertThat(baby.babyWeightDecigrams).isEqualTo(45_200)
        assertThat(baby.babyLengthMillimeters).isEqualTo(510)
        assertThat(baby.entry.isSynced).isTrue()
    }

    @Test
    fun `toDomainEntries keeps weight and bp as separate entries`() {
        val server = listOf(
            UnifiedEntry(category = "weight", entryTimestamp = TIMESTAMP, serverTimestamp = TIMESTAMP, weight = 750, unit = "lb"),
            UnifiedEntry(category = "bp", entryTimestamp = TIMESTAMP, serverTimestamp = TIMESTAMP, systolic = 120, diastolic = 80, pulse = 72),
        )

        val domain = server.toDomainEntries(ACCOUNT_ID)

        assertThat(domain).hasSize(2)
        assertThat(domain.any { it is ScaleEntry }).isTrue()
        assertThat(domain.any { it is BpmEntry }).isTrue()
    }
}
