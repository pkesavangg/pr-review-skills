package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-JVM unit tests for [ButtonType.isInlineText]. The predicate is the
 * single source of truth for the inline-text exclusion in
 * [AppButtonDefaults.horizontalPadding] and the height-modifier branch in
 * [AppButton], so getting its membership right is what matters.
 */
class AppButtonTest {

    /** Variants that opt out of fixed height + horizontal padding to sit flush. */
    private val inlineTypes = setOf(
        ButtonType.InlineTextPrimary,
        ButtonType.InlineTextSecondary,
        ButtonType.InlineTextTertiary,
        ButtonType.ErrorText,
    )

    @Test
    fun `isInlineText is true for the inline-text variants and ErrorText`() {
        inlineTypes.forEach { type ->
            assertThat(type.isInlineText).isTrue()
        }
    }

    @Test
    fun `isInlineText is false for every non-inline ButtonType`() {
        val nonInline = ButtonType.entries.toSet() - inlineTypes
        assertThat(nonInline).isNotEmpty()
        nonInline.forEach { type ->
            assertThat(type.isInlineText).isFalse()
        }
    }

    @Test
    fun `autoSize caps at the design font size and shrinks to the legible minimum`() {
        // A long label that would clip on one line falls back to shrinking text
        // (MOB-174). The cap must equal the style's design size so default-scale
        // buttons render unchanged, and the floor keeps the label legible.
        val designStyle = TextStyle(fontSize = 16.sp)

        assertThat(AppButtonDefaults.autoSize(designStyle))
            .isEqualTo(
                TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = 16.sp,
                    stepSize = 0.5.sp,
                ),
            )
    }

    @Test
    fun `autoSize cap tracks the per-size design font size`() {
        // Small buttons use a smaller design size; the cap must follow it so a
        // small button never renders larger than intended.
        val smallStyle = TextStyle(fontSize = 14.sp)

        assertThat(AppButtonDefaults.autoSize(smallStyle))
            .isEqualTo(
                TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = 14.sp,
                    stepSize = 0.5.sp,
                ),
            )
    }
}
