package com.dmdbrands.gurus.weight.features.common.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-JVM unit tests for [ButtonType.isInlineText]. The predicate is the
 * single source of truth for the inline-text exclusion in
 * [AppButtonDefaults.horizontalPadding] and the height-modifier branch in
 * [AppButton], so getting its membership right is what matters.
 */
class AppButtonTest {

    @Test
    fun `isInlineText is true for InlineTextPrimary, Secondary, and Tertiary`() {
        listOf(
            ButtonType.InlineTextPrimary,
            ButtonType.InlineTextSecondary,
            ButtonType.InlineTextTertiary,
        ).forEach { type ->
            assertThat(type.isInlineText).isTrue()
        }
    }

    @Test
    fun `isInlineText is false for every non-inline ButtonType`() {
        val nonInline = ButtonType.entries.toSet() - setOf(
            ButtonType.InlineTextPrimary,
            ButtonType.InlineTextSecondary,
            ButtonType.InlineTextTertiary,
        )
        assertThat(nonInline).isNotEmpty()
        nonInline.forEach { type ->
            assertThat(type.isInlineText).isFalse()
        }
    }
}
