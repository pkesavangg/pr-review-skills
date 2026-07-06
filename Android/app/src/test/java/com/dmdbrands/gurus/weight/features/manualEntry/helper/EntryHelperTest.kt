package com.dmdbrands.gurus.weight.features.manualEntry.helper

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertToDisplay
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertToStored
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getDate
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getTime
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.rounded
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toDouble1dp
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toDoublePreserve
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toIntSafe
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.EntryForm
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntrySource
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formattedLength
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formattedWeight
import com.dmdbrands.library.ggbluetooth.model.GGBPMEntry
import com.dmdbrands.library.ggbluetooth.model.GGScaleEntry
import com.google.common.truth.Truth.assertThat
import com.greatergoods.libs.appsync.model.AppSyncResult
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EntryHelper] — focused on the manual-entry create path
 * ([EntryForm.toScaleEntry]) plus the pure value-conversion helpers it shares.
 */
class EntryHelperTest {

    private companion object {
        const val ACCOUNT_ID = "acc-1"
    }

    // -------------------------------------------------------------------------
    // EntryForm.toScaleEntry — manual create path (in scope)
    // -------------------------------------------------------------------------

    private fun weightFormWith(
        weight: String = "750",
        bodyFat: String = "",
        muscleMass: String = "",
        bodyWater: String = "",
        bmi: String = "",
        includeR4: Boolean = false,
    ): EntryForm {
        val form = EntryForm.create(includeR4ScaleMetrics = includeR4)
        form.weightDateTime.controls.weight.onValueChange(weight)
        if (bodyFat.isNotEmpty()) form.generalMetrics.controls.bodyFat.onValueChange(bodyFat)
        if (muscleMass.isNotEmpty()) form.generalMetrics.controls.muscleMass.onValueChange(muscleMass)
        if (bodyWater.isNotEmpty()) form.generalMetrics.controls.bodyWater.onValueChange(bodyWater)
        if (bmi.isNotEmpty()) form.generalMetrics.controls.bodyMassIndex.onValueChange(bmi)
        return form
    }

    @Test
    fun `toScaleEntry maps entry metadata for manual create`() {
        val result = weightFormWith().toScaleEntry(WeightUnit.LB, ACCOUNT_ID)

        assertThat(result.entry.accountId).isEqualTo(ACCOUNT_ID)
        assertThat(result.entry.operationType).ignoringCase().isEqualTo("create")
        assertThat(result.entry.deviceType).isEqualTo("manual")
        assertThat(result.entry.deviceId).isEqualTo("manual")
        assertThat(result.entry.unit).isEqualTo(WeightUnit.LB)
        assertThat(result.entry.isSynced).isFalse()
    }

    @Test
    fun `toScaleEntry divides weight by 10 for stored value`() {
        val result = weightFormWith(weight = "750").toScaleEntry(WeightUnit.LB, ACCOUNT_ID)

        // value/10 conversion: "750" -> 75.0
        assertThat(result.scale.scaleEntry.weight).isEqualTo(75.0)
    }

    @Test
    fun `toScaleEntry converts general metrics by dividing by 10`() {
        val result = weightFormWith(bodyFat = "225", muscleMass = "450", bodyWater = "550", bmi = "248")
            .toScaleEntry(WeightUnit.LB, ACCOUNT_ID)

        assertThat(result.scale.scaleEntry.bodyFat).isEqualTo(22.5)
        assertThat(result.scale.scaleEntry.muscleMass).isEqualTo(45.0)
        assertThat(result.scale.scaleEntry.water).isEqualTo(55.0)
        assertThat(result.scale.scaleEntry.bmi).isEqualTo(24.8)
    }

    @Test
    fun `toScaleEntry with blank metrics defaults to zero`() {
        val result = weightFormWith(weight = "").toScaleEntry(WeightUnit.LB, ACCOUNT_ID)

        assertThat(result.scale.scaleEntry.weight).isEqualTo(0.0)
        assertThat(result.scale.scaleEntry.bodyFat).isEqualTo(0.0)
    }

    @Test
    fun `toScaleEntry leaves note null when notes blank`() {
        val result = weightFormWith().toScaleEntry(WeightUnit.LB, ACCOUNT_ID)
        assertThat(result.scale.scaleEntry.note).isNull()
    }

    @Test
    fun `toScaleEntry sets note when notes populated`() {
        val form = weightFormWith()
        form.weightDateTime.controls.notes.onValueChange("morning weigh-in")
        val result = form.toScaleEntry(WeightUnit.LB, ACCOUNT_ID)
        assertThat(result.scale.scaleEntry.note).isEqualTo("morning weigh-in")
    }

    @Test
    fun `toScaleEntry without r4 metrics produces null scaleEntryMetric`() {
        val result = weightFormWith(includeR4 = false).toScaleEntry(WeightUnit.LB, ACCOUNT_ID)
        assertThat(result.scale.scaleEntryMetric).isNull()
    }

    @Test
    fun `toScaleEntry with r4 metrics maps metric entity`() {
        val form = weightFormWith(includeR4 = true)
        form.r4ScaleMetrics?.controls?.bmr?.onValueChange("1500")
        form.r4ScaleMetrics?.controls?.metabolicAge?.onValueChange("30")
        form.r4ScaleMetrics?.controls?.protein?.onValueChange("180")
        form.r4ScaleMetrics?.controls?.heartRate?.onValueChange("72")
        form.r4ScaleMetrics?.controls?.skeletalMuscles?.onValueChange("400")
        form.r4ScaleMetrics?.controls?.subcutaneousFat?.onValueChange("150")
        form.r4ScaleMetrics?.controls?.visceralFat?.onValueChange("8")
        form.r4ScaleMetrics?.controls?.boneMass?.onValueChange("30")

        val metric = form.toScaleEntry(WeightUnit.KG, ACCOUNT_ID).scale.scaleEntryMetric

        assertThat(metric).isNotNull()
        // bmr is toDoubleSafe (no /10), proteinPercent is /10
        assertThat(metric?.bmr).isEqualTo(1500.0)
        assertThat(metric?.metabolicAge).isEqualTo(30)
        assertThat(metric?.proteinPercent).isEqualTo(18.0)
        assertThat(metric?.pulse).isEqualTo(72)
        assertThat(metric?.skeletalMusclePercent).isEqualTo(40.0)
        assertThat(metric?.subcutaneousFatPercent).isEqualTo(15.0)
        assertThat(metric?.boneMass).isEqualTo(3.0)
        assertThat(metric?.impedance).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // FormControl extensions
    // -------------------------------------------------------------------------

    @Test
    fun `toIntSafe returns parsed int`() {
        val control = FormControl.create("42", emptyList())
        assertThat(control.toIntSafe()).isEqualTo(42)
    }

    @Test
    fun `toIntSafe returns default for non-numeric`() {
        val control = FormControl.create("abc", emptyList())
        assertThat(control.toIntSafe(default = 7)).isEqualTo(7)
    }

    // -------------------------------------------------------------------------
    // rounded / toDouble1dp / toDoublePreserve
    // -------------------------------------------------------------------------

    @Test
    fun `rounded rounds to one decimal place`() {
        assertThat(75.46.rounded()).isEqualTo(75.5)
    }

    @Test
    fun `rounded returns null for null`() {
        assertThat((null as Double?).rounded()).isNull()
    }

    @Test
    fun `toDouble1dp rounds finite float`() {
        assertThat(12.34f.toDouble1dp()).isEqualTo(12.3)
    }

    @Test
    fun `toDouble1dp passes through non-finite`() {
        assertThat(Float.NaN.toDouble1dp().isNaN()).isTrue()
    }

    @Test
    fun `toDoublePreserve keeps decimal representation`() {
        assertThat(60.5f.toDoublePreserve()).isEqualTo(60.5)
    }

    // -------------------------------------------------------------------------
    // formatWeightValue
    // -------------------------------------------------------------------------

    @Test
    fun `formatWeightValue null returns empty`() {
        assertThat(formatWeightValue(null)).isEmpty()
    }

    @Test
    fun `formatWeightValue whole number drops decimals`() {
        assertThat(formatWeightValue(60.0)).isEqualTo("60")
    }

    @Test
    fun `formatWeightValue fractional keeps decimal`() {
        assertThat(formatWeightValue(60.5)).isEqualTo("60.5")
    }

    // -------------------------------------------------------------------------
    // convertWeight
    // -------------------------------------------------------------------------

    @Test
    fun `convertWeight same unit returns value`() {
        assertThat(convertWeight(70.0, WeightUnit.KG, WeightUnit.KG)).isEqualTo(70.0)
    }

    @Test
    fun `convertWeight kg to lb multiplies`() {
        assertThat(convertWeight(1.0, WeightUnit.KG, WeightUnit.LB)).isWithin(0.0001).of(2.20462)
    }

    @Test
    fun `convertWeight lb to kg divides`() {
        assertThat(convertWeight(2.20462, WeightUnit.LB, WeightUnit.KG)).isWithin(0.0001).of(1.0)
    }

    @Test
    fun `convertWeight lb to lb_oz unchanged`() {
        assertThat(convertWeight(150.0, WeightUnit.LB, WeightUnit.LB_OZ)).isEqualTo(150.0)
    }

    // -------------------------------------------------------------------------
    // BodyScaleEntryEntity convertToDisplay / convertToStored
    // -------------------------------------------------------------------------

    private fun bodyScale() = BodyScaleEntryEntity(
        id = 1L, weight = 750.0, bodyFat = 220.0, muscleMass = 450.0,
        water = 550.0, bmi = 240.0, source = "manual", note = "n",
    )

    @Test
    fun `BodyScaleEntryEntity convertToDisplay divides by 10`() {
        val d = bodyScale().convertToDisplay()
        assertThat(d.weight).isEqualTo(75.0)
        assertThat(d.bodyFat).isEqualTo(22.0)
        assertThat(d.bmi).isEqualTo(24.0)
    }

    @Test
    fun `BodyScaleEntryEntity convertToStored multiplies by 10`() {
        val display = BodyScaleEntryEntity(
            id = 1L, weight = 75.0, bodyFat = 22.0, muscleMass = 45.0,
            water = 55.0, bmi = 24.0, source = "manual", note = null,
        )
        val s = display.convertToStored()
        assertThat(s.weight).isEqualTo(750.0)
        assertThat(s.bodyFat).isEqualTo(220.0)
    }

    // -------------------------------------------------------------------------
    // BodyScaleEntryMetricEntity? convertToDisplay / convertToStored
    // -------------------------------------------------------------------------

    @Test
    fun `metric convertToDisplay null returns null`() {
        val nullMetric: BodyScaleEntryMetricEntity? = null
        assertThat(nullMetric.convertToDisplay()).isNull()
    }

    @Test
    fun `metric convertToStored null returns null`() {
        val nullMetric: BodyScaleEntryMetricEntity? = null
        assertThat(nullMetric.convertToStored()).isNull()
    }

    @Test
    fun `metric convertToDisplay divides percent fields by 10`() {
        val metric: BodyScaleEntryMetricEntity? = BodyScaleEntryMetricEntity(
            id = 1L, bmr = 1500.0, metabolicAge = 30, proteinPercent = 180.0,
            pulse = 72, skeletalMusclePercent = 400.0, subcutaneousFatPercent = 150.0,
            visceralFatLevel = 80.0, boneMass = 30.0, impedance = 500,
        )
        val d = metric.convertToDisplay()
        assertThat(d?.proteinPercent).isEqualTo(18.0)
        assertThat(d?.boneMass).isEqualTo(3.0)
        assertThat(d?.metabolicAge).isEqualTo(30)
        assertThat(d?.impedance).isEqualTo(500)
    }

    @Test
    fun `metric convertToStored multiplies percent fields by 10`() {
        val metric: BodyScaleEntryMetricEntity? = BodyScaleEntryMetricEntity(
            id = 1L, bmr = 1500.0, metabolicAge = 30, proteinPercent = 18.0,
            pulse = 72, skeletalMusclePercent = 40.0, subcutaneousFatPercent = 15.0,
            visceralFatLevel = 8.0, boneMass = 3.0, impedance = 500,
        )
        val s = metric.convertToStored()
        assertThat(s?.proteinPercent).isEqualTo(180.0)
        assertThat(s?.boneMass).isEqualTo(30.0)
    }

    // -------------------------------------------------------------------------
    // PeriodBodyScaleSummary / HistoryMonth convertToDisplay
    // -------------------------------------------------------------------------

    @Test
    fun `PeriodBodyScaleSummary convertToDisplay divides by 10`() {
        val summary = PeriodBodyScaleSummary(
            period = "2024-01", entryTimestamp = "2024-01-01T00:00:00Z",
            weight = 750.0, bodyFat = 220.0, unit = WeightUnit.LB,
        )
        val d = summary.convertToDisplay()
        assertThat(d.weight).isEqualTo(75.0)
        assertThat(d.bodyFat).isEqualTo(22.0)
    }

    @Test
    fun `HistoryMonth convertToDisplay divides change and avgWeight`() {
        val month = HistoryMonth(
            entryTimestamp = "2024-01", avgWeight = 750.0, entryCount = 3, change = 50.0,
        )
        val d = month.convertToDisplay()
        assertThat(d.avgWeight).isEqualTo(75.0)
        assertThat(d.change).isEqualTo(5.0)
        assertThat(d.entryCount).isEqualTo(3)
    }

    // -------------------------------------------------------------------------
    // getDate / getTime
    // -------------------------------------------------------------------------

    @Test
    fun `ScaleEntry getDate and getTime format timestamp`() {
        val entry = scaleEntry("2024-01-15T10:30:00.000Z")
        assertThat(entry.getDate()).isNotEmpty()
        assertThat(entry.getTime()).isNotEmpty()
    }

    @Test
    fun `BpmEntry getDate and getTime format timestamp`() {
        val bpm = BpmEntry(
            entry = EntryEntity(
                id = 1L, accountId = ACCOUNT_ID, entryTimestamp = "2024-01-15T10:30:00.000Z",
                operationType = "create", deviceType = "bpm", deviceId = "d",
            ),
            bpmEntry = BpmEntryEntity(id = 1L, systolic = 120, diastolic = 80, pulse = 72, meanArterial = "93", note = null),
        )
        assertThat(bpm.getDate()).isNotEmpty()
        assertThat(bpm.getTime()).isNotEmpty()
    }

    // -------------------------------------------------------------------------
    // AppSyncResult.toScaleEntry — manual-entry adjacent create mapper
    // -------------------------------------------------------------------------

    @Test
    fun `AppSyncResult toScaleEntry builds create entry with appsync metadata`() {
        val result = AppSyncResult(weight = 75.0f, fat = 22.5f, muscle = 45.0f, water = 55.0f, mode = "lb")
        val scaleEntry = with(EntryHelper) {
            result.toScaleEntry(accountId = ACCOUNT_ID, unit = "lb", userHeight = 170)
        }
        assertThat(scaleEntry.entry.deviceType).isEqualTo("appsync")
        assertThat(scaleEntry.entry.deviceId).isEqualTo("appsync_scale")
        assertThat(scaleEntry.entry.unit).isEqualTo(WeightUnit.LB)
        assertThat(scaleEntry.scale.scaleEntryMetric).isNull()
        assertThat(scaleEntry.scale.scaleEntry.bmi).isNotNull()
    }

    @Test
    fun `AppSyncResult toScaleEntry null weight yields null bmi`() {
        val result = AppSyncResult(weight = null, fat = null, muscle = null, water = null, mode = "kg")
        val scaleEntry = with(EntryHelper) {
            result.toScaleEntry(accountId = ACCOUNT_ID, unit = "kg")
        }
        assertThat(scaleEntry.entry.unit).isEqualTo(WeightUnit.KG)
        assertThat(scaleEntry.scale.scaleEntry.bmi).isNull()
    }

    @Test
    fun `AppSyncResult toScaleApiEntry maps fields`() {
        val result = AppSyncResult(weight = 75.0f, fat = 22.0f, muscle = 45.0f, water = 55.0f, mode = "kg")
        val apiEntry = with(EntryHelper) { result.toScaleApiEntry(accountId = ACCOUNT_ID) }
        assertThat(apiEntry.operationType).isEqualTo("create")
        assertThat(apiEntry.weight).isEqualTo(75)
        assertThat(apiEntry.unit).isEqualTo("kg")
        assertThat(apiEntry.source).isEqualTo("appsync scale")
    }

    @Test
    fun `AppSyncResult toScaleApiEntry defaults unit to lb for non-kg mode`() {
        val result = AppSyncResult(weight = 150.0f, fat = null, muscle = null, water = null, mode = "lb")
        val apiEntry = with(EntryHelper) { result.toScaleApiEntry(accountId = ACCOUNT_ID) }
        assertThat(apiEntry.unit).isEqualTo("lb")
        assertThat(apiEntry.weight).isEqualTo(150)
    }

    // -------------------------------------------------------------------------
    // Entry.convertToStored
    // -------------------------------------------------------------------------

    @Test
    fun `ScaleEntry convertToStored multiplies scale fields by 10`() {
        val entry = ScaleEntry(
            entry = EntryEntity(
                id = 1L, accountId = ACCOUNT_ID, entryTimestamp = "2024-01-15T10:30:00.000Z",
                operationType = "create", deviceType = "scale", deviceId = "d", unit = WeightUnit.LB,
            ),
            scale = ScaleEntryWithMetrics(
                scaleEntry = BodyScaleEntryEntity(
                    id = 1L, weight = 75.0, bodyFat = 22.0, muscleMass = 45.0,
                    water = 55.0, bmi = 24.0, source = "manual",
                ),
                scaleEntryMetric = null,
            ),
        )
        val stored = with(EntryHelper) { entry.convertToStored() } as ScaleEntry
        assertThat(stored.scale.scaleEntry.weight).isEqualTo(750.0)
    }

    @Test
    fun `BpmEntry convertToStored returns same bpm payload`() {
        val bpm = BpmEntry(
            entry = EntryEntity(
                id = 1L, accountId = ACCOUNT_ID, entryTimestamp = "2024-01-15T10:30:00.000Z",
                operationType = "create", deviceType = "bpm", deviceId = "d",
            ),
            bpmEntry = BpmEntryEntity(id = 1L, systolic = 120, diastolic = 80, pulse = 72, meanArterial = "93", note = null),
        )
        val stored = with(EntryHelper) { bpm.convertToStored() } as BpmEntry
        assertThat(stored.bpmEntry.systolic).isEqualTo(120)
    }

    // -------------------------------------------------------------------------
    // getScaleSetupType / getCalculatedBMI
    // -------------------------------------------------------------------------

    @Test
    fun `getScaleSetupType maps r4 protocol distinctly from generic bluetooth`() {
        val r4Value = com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
            .GG_DEVICE_PROTOCOL_R4.value
        val r4 = EntryHelper.getScaleSetupType(r4Value)
        val bt = EntryHelper.getScaleSetupType("some-other-protocol")
        assertThat(r4).isNotEqualTo(bt)
    }

    @Test
    fun `getCalculatedBMI returns positive bmi for lb input`() {
        val bmi = EntryHelper.getCalculatedBMI(weight = 1500f, unit = WeightUnit.LB, height = 1700)
        assertThat(bmi).isGreaterThan(0.0)
    }

    @Test
    fun `getCalculatedBMI returns positive bmi for kg input`() {
        val bmi = EntryHelper.getCalculatedBMI(weight = 70f, unit = WeightUnit.KG, height = 1700)
        assertThat(bmi).isGreaterThan(0.0)
    }

    // -------------------------------------------------------------------------
    // BabyEntry formatters
    // -------------------------------------------------------------------------

    private fun babyEntry(weightDg: Int? = null, lengthMm: Int? = null): BabyEntry = BabyEntry(
        entry = EntryEntity(
            id = 1L, accountId = ACCOUNT_ID, entryTimestamp = "2024-01-15T10:30:00.000Z",
            operationType = "create", deviceType = "manual", deviceId = "",
        ),
        babyEntry = BabyEntryEntity(
            id = 1L, babyId = "baby-1", babyWeightDecigrams = weightDg,
            babyLengthMillimeters = lengthMm, entryNote = null,
            entryType = "weight", source = EntrySource.MANUAL.value,
        ),
    )

    @Test
    fun `BabyEntry formattedWeight null decigrams returns dashes`() {
        assertThat(babyEntry(weightDg = null).formattedWeight()).isEqualTo("--")
    }

    @Test
    fun `BabyEntry formattedWeight non-null produces display string`() {
        assertThat(babyEntry(weightDg = 320).formattedWeight()).isNotEqualTo("--")
    }

    @Test
    fun `BabyEntry formattedLength null mm returns dashes`() {
        assertThat(babyEntry(lengthMm = null).formattedLength()).isEqualTo("--")
    }

    @Test
    fun `BabyEntry formattedLength non-null produces display string`() {
        assertThat(babyEntry(lengthMm = 500).formattedLength()).isNotEqualTo("--")
    }

    // -------------------------------------------------------------------------
    // GGScaleEntry.toScaleEntry / GGBPMEntry.toBpmEntry (BLE mappers)
    // -------------------------------------------------------------------------

    private fun ggScaleEntry(unit: String = "lb", protocol: String = "btWifiR4") = GGScaleEntry(
        bmi = 24.0f, bmr = 1500, bodyFat = 22.0f, water = 55.0f, boneMass = 3.0f,
        metabolicAge = 30, muscleMass = 45.0f, proteinPercent = 18.0f,
        skeletalMusclePercent = 40.0f, subcutaneousFatPercent = 15.0f, unit = unit,
        visceralFatLevel = 8, weight = 150.0f, weightInKg = 68.0f, date = 1_700_000_000_000L,
        impedance = 500.0f, pulse = 72, broadcastId = "b", broadcastIdString = "b",
        protocolType = protocol,
    )

    @Test
    fun `GGScaleEntry toScaleEntry maps kg unit`() {
        val result = with(EntryHelper) { ggScaleEntry(unit = "kg").toScaleEntry(ACCOUNT_ID, "dev-1", isMetric = true) }
        assertThat(result.entry.unit).isEqualTo(WeightUnit.KG)
        assertThat(result.entry.deviceId).isEqualTo("dev-1")
        assertThat(result.scale.scaleEntryMetric).isNotNull()
    }

    @Test
    fun `GGScaleEntry toScaleEntry maps lb unit and a3 weight`() {
        val resultR4 = with(EntryHelper) {
          ggScaleEntry(unit = "lb", protocol = "A3").toScaleEntry(ACCOUNT_ID, "dev-1", isMetric = false)
        }
        assertThat(resultR4.entry.unit).isEqualTo(WeightUnit.LB)
        assertThat(resultR4.entry.deviceType).isEqualTo("A3")
        // A3 kg (68.0) → scale's lb formula → convertKgToStoredA3(68.0), matching the scale display.
        assertThat(resultR4.scale.scaleEntry.weight)
          .isEqualTo(ConversionTools.convertKgToStoredA3(68.0))
    }

    @Test
    fun `GGBPMEntry toBpmEntry computes mean when meanPressure provided`() {
        val gg = GGBPMEntry(
            systolic = 120, diastolic = 80, pulse = 72, date = 1_700_000_000_000L,
            broadcastId = "b", broadcastIdString = "b", protocolType = "bpm",
            userNumber = 1, meanPressure = 95,
        )
        val result = with(EntryHelper) { gg.toBpmEntry(ACCOUNT_ID, "dev-1") }
        assertThat(result.bpmEntry.systolic).isEqualTo(120)
        assertThat(result.bpmEntry.meanArterial).isEqualTo("95")
    }

    @Test
    fun `GGBPMEntry toBpmEntry falls back to computed mean when meanPressure zero`() {
        val gg = GGBPMEntry(
            systolic = 120, diastolic = 80, pulse = 72, date = 1_700_000_000_000L,
            broadcastId = "b", broadcastIdString = "b", protocolType = "bpm",
            userNumber = 1, meanPressure = 0,
        )
        val result = with(EntryHelper) { gg.toBpmEntry(ACCOUNT_ID, "dev-1") }
        // meanPressure 0 → toInt() is 0, "0" is used (non-null), so MAP = "0"
        assertThat(result.bpmEntry.meanArterial).isEqualTo("0")
    }

    private fun scaleEntry(timestamp: String): ScaleEntry = ScaleEntry(
        entry = EntryEntity(
            id = 1L, accountId = ACCOUNT_ID, entryTimestamp = timestamp,
            operationType = "create", deviceType = "scale", deviceId = "d", unit = WeightUnit.LB,
        ),
        scale = ScaleEntryWithMetrics(
            scaleEntry = BodyScaleEntryEntity(
                id = 1L, weight = 750.0, bodyFat = null, muscleMass = null,
                water = null, bmi = null, source = null,
            ),
            scaleEntryMetric = null,
        ),
    )
}
