package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.google.common.truth.Truth.assertThat
import org.junit.Test

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

    // ── Entry.toUnifiedRequestOrNull ─────────────────────────────────────────────

    @Test
    fun `toUnifiedRequestOrNull dispatches weight and bp`() {
        assertThat(scaleEntry().toUnifiedRequestOrNull()?.category).isEqualTo("weight")
        assertThat(bpmEntry().toUnifiedRequestOrNull()?.category).isEqualTo("bp")
    }

    @Test
    fun `toUnifiedRequestOrNull drops a zero-weight reading`() {
        val zeroWeight = scaleEntry().let {
            it.copy(scale = it.scale.copy(scaleEntry = it.scale.scaleEntry.copy(weight = 0.0)))
        }
        assertThat(zeroWeight.toUnifiedRequestOrNull()).isNull()
    }

    @Test
    fun `toUnifiedRequestOrNull drops an out-of-range bp reading`() {
        // systolic 0 / diastolic 0 — a garbage reading must not be POSTed as a real entry.
        val garbage = bpmEntry().let {
            it.copy(bpmEntry = it.bpmEntry.copy(systolic = 0, diastolic = 0, pulse = 0))
        }
        assertThat(garbage.toUnifiedRequestOrNull()).isNull()
        // An above-range systolic is also rejected.
        val tooHigh = bpmEntry().let { it.copy(bpmEntry = it.bpmEntry.copy(systolic = 400)) }
        assertThat(tooHigh.toUnifiedRequestOrNull()).isNull()
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
        val unified = UnifiedEntry(category = "baby", entryTimestamp = TIMESTAMP)
        assertThat(unified.toDomainEntry(ACCOUNT_ID)).isNull()
    }
}
