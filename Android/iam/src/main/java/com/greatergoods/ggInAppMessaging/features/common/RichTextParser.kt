package com.greatergoods.ggInAppMessaging.features.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.greatergoods.ggInAppMessaging.domain.models.TIMESTAMP
import com.greatergoods.ggInAppMessaging.domain.models.UnitsOfTime
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Feed template parser equivalent to Angular FeedTemplatePipe.
 * Handles {{expiresAt}} and {{bold[string]}} / {{italic[string]}} etc.
 */
object RichTextParser {

  /**
   * Parses rich text with custom markup and returns AnnotatedString for Compose.
   *
   * @param text The template string (e.g. "Ends in {{expiresAt}}" or "The {{bold[All-in-One]}} sealer")
   * @param expiresAt The expiration date string (ISO format), for {{expiresAt}} substitution
     * @return AnnotatedString with proper styling
     */
    fun parseRichText(text: String, expiresAt: String? = null): AnnotatedString {
    var currentText = text

    // Replace {{expiresAt}} with relative time (matches Angular transformDateToText)
    if (expiresAt != null) {
      currentText = currentText.replace("{{expiresAt}}", transformDateToText(expiresAt))
    }

    return buildAnnotatedString {
      parseStyledTokens(currentText)
    }
    }

  /**
   * Transforms expiry date to relative time string.
   * Matches Angular feed-template.pipe.ts transformDateToText():
   * - compareTime = date - Date.now()
   * - if compareTime > 0: return "a minute" | "X minutes" | "X hours" | "X days " (trailing space for days)
   * - else: return ""
   */
  private fun transformDateToText(expiresAt: String): String {
    return try {
      val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
      val date = inputFormat.parse(expiresAt) ?: return ""
      val compareTime = date.time - System.currentTimeMillis()
      if (compareTime <= 0) return ""
      when {
        compareTime <= TIMESTAMP.ONE_MINUTE -> UnitsOfTime.MINUTE
        compareTime < TIMESTAMP.ONE_HOUR -> {
          val expiresInMinutes = (compareTime / TIMESTAMP.ONE_MINUTE).toInt()
          "$expiresInMinutes ${UnitsOfTime.MINUTES}"
        }
        compareTime < TIMESTAMP.TWO_DAYS -> {
          val expiresInHours = (compareTime / TIMESTAMP.ONE_HOUR).toInt()
          "$expiresInHours ${UnitsOfTime.HOURS}"
        }
        else -> {
          val expiresInDays = (compareTime / TIMESTAMP.ONE_DAY).toInt()
          "$expiresInDays ${UnitsOfTime.DAYS} "
        }
      }
    } catch (e: Exception) {
      ""
    }
  }

    /**
     * Parses styled tokens like {{bold[text]}}, {{strike-bold-italic[text]}}, etc.
     */
    private fun AnnotatedString.Builder.parseStyledTokens(text: String) {
        val tokenRegex = Regex("\\{\\{([^\\[]+)\\[([^]]+)]\\}\\}")
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

}

/**
 * Extension function to easily parse rich text
 */
fun String.parseRichText(expiresAt: String? = null): AnnotatedString {
    return RichTextParser.parseRichText(this, expiresAt)
}
