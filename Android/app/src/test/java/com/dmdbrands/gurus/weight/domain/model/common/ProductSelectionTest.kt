package com.dmdbrands.gurus.weight.domain.model.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ProductSelectionTest {

    private fun baby(id: String, name: String = "Mia", isSynced: Boolean = false, existsOnServer: Boolean = false) =
        ProductSelection.Baby(
            BabyProfile(id = id, accountId = "acc-1", name = name, isSynced = isSynced, existsOnServer = existsOnServer),
        )

    @Test
    fun `same baby id matches even when transient sync flags differ`() {
        // Two representations of the same baby from different sources (list vs synced) differ by
        // isSynced/existsOnServer — structural == would fail, identity match must not (MOB-1476).
        val fromList = baby(id = "srv-1", isSynced = false, existsOnServer = false)
        val fromSynced = baby(id = "srv-1", isSynced = true, existsOnServer = true)

        assertThat(fromList.isSameSelectionAs(fromSynced)).isTrue()
        assertThat(fromList == fromSynced).isFalse()
    }

    @Test
    fun `different baby ids do not match`() {
        assertThat(baby(id = "a").isSameSelectionAs(baby(id = "b"))).isFalse()
    }

    @Test
    fun `singleton products match by type`() {
        assertThat(ProductSelection.MyWeight.isSameSelectionAs(ProductSelection.MyWeight)).isTrue()
        assertThat(ProductSelection.BloodPressure.isSameSelectionAs(ProductSelection.BloodPressure)).isTrue()
    }

    @Test
    fun `baby does not match a non-baby product or null`() {
        assertThat(baby(id = "a").isSameSelectionAs(ProductSelection.MyWeight)).isFalse()
        assertThat(baby(id = "a").isSameSelectionAs(null)).isFalse()
    }

    @Test
    fun `Baby and BabyScale share productType but are distinct selections`() {
        // Both are ProductType.BABY; the class check keeps them apart.
        assertThat(ProductSelection.BabyScale.isSameSelectionAs(baby(id = "a"))).isFalse()
    }
}
