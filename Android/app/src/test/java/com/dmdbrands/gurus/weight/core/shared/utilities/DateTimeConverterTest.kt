package com.dmdbrands.gurus.weight.core.shared.utilities

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DateTimeConverterTest {

    // -------------------------------------------------------------------------
    // isBirthDate — baby birthday balloon match
    // -------------------------------------------------------------------------

    @Test
    fun `isBirthDate matches an ISO birthdate with a T time component`() {
        assertThat(DateTimeConverter.isBirthDate("2024-08-17", "2024-08-17T00:00:00.000Z")).isTrue()
    }

    @Test
    fun `isBirthDate matches a space-separated birthdate`() {
        assertThat(DateTimeConverter.isBirthDate("2024-08-17", "2024-08-17 10:30:00")).isTrue()
    }

    @Test
    fun `isBirthDate matches a plain date birthdate`() {
        assertThat(DateTimeConverter.isBirthDate("2024-08-17", "2024-08-17")).isTrue()
    }

    @Test
    fun `isBirthDate is false when the day differs`() {
        assertThat(DateTimeConverter.isBirthDate("2024-08-18", "2024-08-17T00:00:00.000Z")).isFalse()
    }

    @Test
    fun `isBirthDate is false for a different year (exact date only)`() {
        // Exact birth date only — a later birthday anniversary is NOT a match.
        assertThat(DateTimeConverter.isBirthDate("2025-08-17", "2024-08-17T00:00:00.000Z")).isFalse()
    }

    @Test
    fun `isBirthDate is false for null, blank, or too-short birthdate`() {
        assertThat(DateTimeConverter.isBirthDate("2024-08-17", null)).isFalse()
        assertThat(DateTimeConverter.isBirthDate("2024-08-17", "")).isFalse()
        assertThat(DateTimeConverter.isBirthDate("2024-08-17", "2024-08")).isFalse()
    }
}
