package com.greatergoods.meapp.features.common.components.input

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
        val originalText = text.text
        val digitsOnly = originalText.filter { it.isDigit() }

        if (digitsOnly.isEmpty()) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }

        val totalLength = digitsOnly.length
        val integerPartLength = max(0, totalLength - decimalDigits)
        val fractionalPartLength = min(totalLength, decimalDigits)

        val integerPart = digitsOnly.substring(0, integerPartLength)
        val fractionalPart = digitsOnly.substring(integerPartLength)

        val formattedText = buildString {
            if (integerPart.isEmpty() && fractionalPart.isNotEmpty()) {
                append('0')
            } else {
                append(integerPart)
            }
            if (decimalDigits > 0 && fractionalPart.isNotEmpty()) {
                append('.')
                append(fractionalPart)
            } else if (decimalDigits > 0 && integerPart.isNotEmpty() && originalText.endsWith(".")) {
                // Handle case where user types "123." and expects "123.0" (if decimalDigits = 1)
                // For now, if digitsOnly is not empty, we always show the fractional part based on digits
                // This part might need refinement if we want to auto-append "0"
                 append('.')
                 append("0".repeat(decimalDigits)) // Default to .0 or .00
            } else if (decimalDigits > 0 && integerPart.isEmpty() && fractionalPart.isEmpty()){
                 append('0')
                 append('.')
                 append("0".repeat(decimalDigits))
            }
        }

        // A more robust way to handle empty input or just "0"
        val finalTextToShow = if (digitsOnly == "0".repeat(digitsOnly.length) && integerPartLength == 0 && fractionalPartLength <= decimalDigits) {
            // If input is "0", "00", etc. and it's meant for the fractional part
            buildString {
                append("0.")
                append("0".repeat(max(0, decimalDigits - digitsOnly.length)))
                append(digitsOnly.substring(max(0, digitsOnly.length - decimalDigits)))
            }.removeSuffix(".")
        } else if (digitsOnly.isEmpty()){
            ""
        }
        else {
            formattedText
        }


        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (originalText.isEmpty()) return 0
                val digitsBeforeCursor = originalText.substring(0, offset).count { it.isDigit() }

                return if (digitsBeforeCursor > integerPartLength) {
                    // Cursor is in or after the fractional part
                    digitsBeforeCursor + 1 // +1 for the decimal point
                } else if (digitsBeforeCursor == integerPartLength && integerPartLength > 0 && fractionalPart.isNotEmpty()) {
                     // Cursor is at the end of integer part, before decimal
                    digitsBeforeCursor + 1
                } else if (digitsBeforeCursor == 0 && integerPartLength == 0 && fractionalPart.isNotEmpty()){
                    // "0." case, cursor before first digit of fractional part
                    2 // after "0."
                }
                else {
                    // Cursor is in the integer part
                    digitsBeforeCursor
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val transformed = finalTextToShow
                if (offset == 0) return 0
                if (offset > transformed.length) return originalText.length

                val charsBeforeCursorInTransformed = transformed.substring(0, offset)
                var digitCount = 0
                var hasPassedDecimal = false

                for(char in charsBeforeCursorInTransformed) {
                    if (char.isDigit()) {
                        digitCount++
                    }
                    if (char == '.') {
                        hasPassedDecimal = true
                    }
                }
                 // This is a simplified mapping. A more accurate one would track digit positions.
                // For now, it tries to map back based on digit count.
                var originalOffset = 0
                var currentDigitsCounted = 0
                for(char_orig in originalText){
                    if(char_orig.isDigit()){
                        currentDigitsCounted++
                    }
                    originalOffset++
                    if(currentDigitsCounted == digitCount){
                        break
                    }
                }
                return min(originalOffset, originalText.length)
            }
        }

        return TransformedText(AnnotatedString(finalTextToShow), offsetMapping)
    }
}
