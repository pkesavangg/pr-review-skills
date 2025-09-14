package com.greatergoods.ggInAppMessaging.features.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parser for rich text with custom markup tokens
 * Handles tokens like {{expiresAt}}, {{bold[text]}}, {{strike-bold-italic[text]}}, etc.
 */
object RichTextParser {

    /**
     * Parses rich text with custom markup and returns AnnotatedString for Compose
     * @param text The text with markup tokens
     * @param expiresAt The expiration date string (ISO format)
     * @return AnnotatedString with proper styling
     */
    fun parseRichText(text: String, expiresAt: String? = null): AnnotatedString {
        return buildAnnotatedString {
            var currentText = text

            // Replace {{expiresAt}} with formatted date
            if (expiresAt != null) {
                currentText = currentText.replace("{{expiresAt}}", formatExpirationDate(expiresAt))
            }

            // Parse and apply styling tokens
            parseStyledTokens(currentText)
        }
    }

    /**
     * Parses styled tokens like {{bold[text]}}, {{strike-bold-italic[text]}}, etc.
     */
    private fun AnnotatedString.Builder.parseStyledTokens(text: String) {
        val tokenRegex = Regex("\\{\\{([^\\[]+)\\[([^\\]]+)\\]\\}\\}")
        var lastIndex = 0

        tokenRegex.findAll(text).forEach { matchResult ->
            val beforeText = text.substring(lastIndex, matchResult.range.first)
            if (beforeText.isNotEmpty()) {
                append(beforeText)
            }

            val styleType = matchResult.groupValues[1]
            val content = matchResult.groupValues[2]

            when (styleType) {
                "bold" -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                }
                "italic" -> {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                }
                "strike" -> {
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(content)
                    }
                }
                "strike-bold" -> {
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.LineThrough
                    )) {
                        append(content)
                    }
                }
                "strike-bold-italic" -> {
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        textDecoration = TextDecoration.LineThrough
                    )) {
                        append(content)
                    }
                }
                "underline" -> {
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(content)
                    }
                }
                else -> {
                    // Unknown style type, just append the content
                    append(content)
                }
            }

            lastIndex = matchResult.range.last + 1
        }

        // Append remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    /**
     * Formats expiration date from ISO string to readable format
     * @param expiresAt ISO date string
     * @return Formatted date string
     */
    private fun formatExpirationDate(expiresAt: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            val date = inputFormat.parse(expiresAt)
            if (date != null) {
                outputFormat.format(date)
            } else {
                expiresAt // Return original if parsing fails
            }
        } catch (e: Exception) {
            expiresAt // Return original if parsing fails
        }
    }
}

/**
 * Extension function to easily parse rich text
 */
fun String.parseRichText(expiresAt: String? = null): AnnotatedString {
    return RichTextParser.parseRichText(this, expiresAt)
}
