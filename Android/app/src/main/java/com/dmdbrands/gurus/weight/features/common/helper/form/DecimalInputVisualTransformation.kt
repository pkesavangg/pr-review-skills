package com.dmdbrands.gurus.weight.features.common.helper.form

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.max
import kotlin.math.min

class DecimalInputVisualTransformation(
    internal val decimalDigits: Int // Changed from private to internal
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return try {
            val originalText = text.text
            val digitsOnly = originalText.filter { it.isDigit() }

            if (digitsOnly.isEmpty()) {
                return TransformedText(
                    AnnotatedString(""),
                    OffsetMapping.Identity
                )
            }

            val totalLength = digitsOnly.length
            val integerPartLength = max(0, totalLength - decimalDigits)
            val fractionalPartLength = min(totalLength, decimalDigits)

            val integerPart = if (integerPartLength > 0) {
                digitsOnly.substring(0, integerPartLength)
            } else {
                ""
            }

            val fractionalPart = if (fractionalPartLength > 0 && integerPartLength < digitsOnly.length) {
                digitsOnly.substring(integerPartLength)
            } else {
                ""
            }

            val formattedText = buildString {
                if (integerPart.isEmpty() && fractionalPart.isNotEmpty()) {
                    append('0')
                } else {
                    append(integerPart)
                }
                if (decimalDigits > 0 && fractionalPart.isNotEmpty()) {
                    append('.')
                    append(fractionalPart)
                } else if (decimalDigits > 0 && integerPart.isNotEmpty() && originalText.contains(".")) {
                    append('.')
                    append("0".repeat(decimalDigits))
                } else if (decimalDigits > 0 && integerPart.isEmpty() && fractionalPart.isEmpty()) {
                    append('0')
                    append('.')
                    append("0".repeat(decimalDigits))
                }
            }

            // Handle special cases more safely
            val finalTextToShow = when {
                digitsOnly.isEmpty() -> ""
                digitsOnly == "0".repeat(digitsOnly.length) && integerPartLength == 0 && fractionalPartLength <= decimalDigits -> {
                    buildString {
                        append("0.")
                        val zerosNeeded = max(0, decimalDigits - digitsOnly.length)
                        append("0".repeat(zerosNeeded))
                        val remainingDigits = digitsOnly.substring(max(0, digitsOnly.length - decimalDigits))
                        append(remainingDigits)
                    }.removeSuffix(".")
                }
                else -> formattedText
            }

            val offsetMapping = createSafeOffsetMapping(originalText, finalTextToShow, integerPartLength, fractionalPart)

            TransformedText(
                AnnotatedString(finalTextToShow),
                offsetMapping
            )
        } catch (e: Exception) {
            // Fallback to identity transformation if anything goes wrong
            TransformedText(text, OffsetMapping.Identity)
        }
    }

    private fun createSafeOffsetMapping(
        originalText: String,
        finalTextToShow: String,
        integerPartLength: Int,
        fractionalPart: String
    ): OffsetMapping {
        return object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return try {
                    if (originalText.isEmpty() || offset <= 0) return 0
                    if (offset > originalText.length) return finalTextToShow.length

                    val safeOffset = offset.coerceIn(0, originalText.length)
                    val digitsBeforeCursor = originalText.substring(0, safeOffset).count { it.isDigit() }

                    when {
                        digitsBeforeCursor > integerPartLength -> {
                            // Cursor is in or after the fractional part
                            (digitsBeforeCursor + 1).coerceAtMost(finalTextToShow.length)
                        }
                        digitsBeforeCursor == integerPartLength && integerPartLength > 0 && fractionalPart.isNotEmpty() -> {
                            // Cursor is at the end of integer part, before decimal
                            (digitsBeforeCursor + 1).coerceAtMost(finalTextToShow.length)
                        }
                        digitsBeforeCursor == 0 && integerPartLength == 0 && fractionalPart.isNotEmpty() -> {
                            // "0." case, cursor before first digit of fractional part
                            2.coerceAtMost(finalTextToShow.length)
                        }
                        else -> {
                            // Cursor is in the integer part
                            digitsBeforeCursor.coerceAtMost(finalTextToShow.length)
                        }
                    }
                } catch (e: Exception) {
                    0
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return try {
                    if (offset <= 0) return 0
                    if (offset > finalTextToShow.length) return originalText.length

                    val safeOffset = offset.coerceIn(0, finalTextToShow.length)
                    val charsBeforeCursor = if (safeOffset > 0) {
                        finalTextToShow.substring(0, safeOffset)
                    } else {
                        ""
                    }

                    var digitCount = 0
                    for (char in charsBeforeCursor) {
                        if (char.isDigit()) {
                            digitCount++
                        }
                    }

                    // Map back based on digit count
                    var originalOffset = 0
                    var currentDigitsCounted = 0

                    for (i in originalText.indices) {
                        if (originalText[i].isDigit()) {
                            currentDigitsCounted++
                        }
                        originalOffset++
                        if (currentDigitsCounted >= digitCount) {
                            break
                        }
                    }

                    originalOffset.coerceAtMost(originalText.length)
                } catch (e: Exception) {
                    originalText.length
                }
            }
        }
    }
}
