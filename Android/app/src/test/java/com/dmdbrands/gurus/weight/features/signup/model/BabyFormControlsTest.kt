package com.dmdbrands.gurus.weight.features.signup.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BabyFormControls] and the [BabyProfile] default it seeds.
 */
class BabyFormControlsTest {

    @Test
    fun `fresh baby form defaults weight unit to lbs-oz per MOB-450`() {
        // The signup add-a-baby step must default to lbs/oz to match the Smart Baby app
        // (UX decision on MOB-450). Guards against a silent regression of the default.
        assertThat(BabyFormControls.create().weightUnit.value).isEqualTo(BabyWeightUnit.LBS_OZ)
        assertThat(BabyProfile().weightUnit).isEqualTo(BabyWeightUnit.LBS_OZ)
    }
}
