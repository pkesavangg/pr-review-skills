package com.dmdbrands.gurus.weight.data.storage.datastore

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.proto.BabyWeightUnit
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for the proto<->WeightUnit conversion used by
 * UserDataStore baby weight unit storage. The UNSPECIFIED → LB_OZ mapping
 * is load-bearing: it backfills the canonical baby unit for accounts
 * created before MOB-410 added the proto field.
 */
class BabyWeightUnitMapperTest {

    @Test
    fun `proto LB_OZ maps to WeightUnit LB_OZ`() {
        assertThat(BabyWeightUnit.BABY_WEIGHT_UNIT_LB_OZ.toWeightUnit()).isEqualTo(WeightUnit.LB_OZ)
    }

    @Test
    fun `proto LB maps to WeightUnit LB`() {
        assertThat(BabyWeightUnit.BABY_WEIGHT_UNIT_LB.toWeightUnit()).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `proto KG maps to WeightUnit KG`() {
        assertThat(BabyWeightUnit.BABY_WEIGHT_UNIT_KG.toWeightUnit()).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `UNSPECIFIED defaults to LB_OZ for unmigrated accounts`() {
        assertThat(BabyWeightUnit.BABY_WEIGHT_UNIT_UNSPECIFIED.toWeightUnit()).isEqualTo(WeightUnit.LB_OZ)
    }

    @Test
    fun `null proto defaults to LB_OZ`() {
        assertThat((null as BabyWeightUnit?).toWeightUnit()).isEqualTo(WeightUnit.LB_OZ)
    }

    @Test
    fun `WeightUnit round-trips through proto`() {
        WeightUnit.entries.forEach { unit ->
            assertThat(unit.toProto().toWeightUnit()).isEqualTo(unit)
        }
    }
}
