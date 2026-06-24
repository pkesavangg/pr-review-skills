package com.dmdbrands.gurus.weight.features.common.helper

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CommonHelperTest {

    @Test
    fun `isPhoneLike is true for Phone`() {
        assertThat(DeviceType.Phone.isPhoneLike).isTrue()
    }

    @Test
    fun `isPhoneLike is true for Fold (folded outer display behaves like a phone)`() {
        assertThat(DeviceType.Fold.isPhoneLike).isTrue()
    }

    @Test
    fun `isPhoneLike is false for Tablet`() {
        assertThat(DeviceType.Tablet.isPhoneLike).isFalse()
    }
}
