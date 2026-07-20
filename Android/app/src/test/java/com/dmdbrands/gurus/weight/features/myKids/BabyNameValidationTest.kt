package com.dmdbrands.gurus.weight.features.myKids

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BabyNameValidationTest {

    @Test
    fun `no siblings is never a duplicate`() {
        assertThat(isDuplicateBabyName("Mia", emptyList())).isFalse()
    }

    @Test
    fun `exact match is a duplicate`() {
        assertThat(isDuplicateBabyName("Mia", listOf("Mia", "Leo"))).isTrue()
    }

    @Test
    fun `match is case-insensitive and trimmed`() {
        assertThat(isDuplicateBabyName("  mIA  ", listOf("Mia"))).isTrue()
        assertThat(isDuplicateBabyName("Leo", listOf("  LEO"))).isTrue()
    }

    @Test
    fun `distinct name is not a duplicate`() {
        assertThat(isDuplicateBabyName("Zoe", listOf("Mia", "Leo"))).isFalse()
    }

    @Test
    fun `excluding the edited baby avoids self-flagging`() {
        // Caller passes siblingNames already excluding the baby being edited; re-saving its own
        // unchanged name is then not a duplicate.
        assertThat(isDuplicateBabyName("Mia", listOf("Leo"))).isFalse()
    }
}
