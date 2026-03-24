package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.cleanCorruptedChars
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.displayName
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.formatTimestamp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.TimeZone

/**
 * Unit tests for [StringUtil].
 *
 * StringUtil contains pure extension functions — no Android context, no mocks,
 * no coroutines needed. Every test is a straight input → output assertion.
 *
 * This file shows the simplest test pattern: call a function, assert the result.
 */
class StringUtilTest {

    private lateinit var originalLocale: Locale
    private lateinit var originalTimeZone: TimeZone

    @BeforeEach
    fun setUp() {
        originalLocale = Locale.getDefault()
        originalTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @AfterEach
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    // -------------------------------------------------------------------------
    // displayName — replaces underscores with spaces
    // -------------------------------------------------------------------------

    @Test
    fun `displayName replaces underscores with spaces`() {
        val result = "body_fat_percentage".displayName()

        assertThat(result).isEqualTo("body fat percentage")
    }

    @Test
    fun `displayName handles string with no underscores`() {
        val result = "weight".displayName()

        assertThat(result).isEqualTo("weight")
    }

    @Test
    fun `displayName handles multiple consecutive underscores`() {
        val result = "foo__bar".displayName()

        assertThat(result).isEqualTo("foo  bar")
    }

    @Test
    fun `displayName returns empty string unchanged`() {
        val result = "".displayName()

        assertThat(result).isEmpty()
    }

    // -------------------------------------------------------------------------
    // formatTimestamp — Unix seconds → human-readable date
    // -------------------------------------------------------------------------

    @Test
    fun `formatTimestamp converts Unix epoch zero to January 01, 1970`() {
        val result = 0L.formatTimestamp()

        assertThat(result).isEqualTo("January 01, 1970")
    }

    @Test
    fun `formatTimestamp converts known timestamp to November 14, 2023`() {
        val result = 1_700_000_000L.formatTimestamp()

        assertThat(result).isEqualTo("November 14, 2023")
    }

    // -------------------------------------------------------------------------
    // cleanCorruptedChars — strips non-printable characters
    // -------------------------------------------------------------------------

    @Test
    fun `cleanCorruptedChars leaves a normal string unchanged`() {
        val result = "Hello, World!".cleanCorruptedChars()

        assertThat(result).isEqualTo("Hello, World!")
    }

    @Test
    fun `cleanCorruptedChars removes null byte`() {
        val input = "abc\u0000def"

        val result = input.cleanCorruptedChars()

        assertThat(result).isEqualTo("abcdef")
    }

    @Test
    fun `cleanCorruptedChars removes multiple non-printable characters`() {
        // \u0001 (SOH) and \u007F (DEL) are non-printable control characters
        val input = "\u0001corrupted\u007Ftext"

        val result = input.cleanCorruptedChars()

        assertThat(result).isEqualTo("corruptedtext")
    }

    @Test
    fun `cleanCorruptedChars returns empty string unchanged`() {
        val result = "".cleanCorruptedChars()

        assertThat(result).isEmpty()
    }
}
