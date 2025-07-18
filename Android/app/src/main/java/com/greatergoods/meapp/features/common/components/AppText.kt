package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.token.LocalSpacing

data class TextAppearance(
  val style: TextStyle,
  val color: Color,
)

enum class AnnotationPosition {
  Start,
  End,
  Middle,
}

enum class TextType {
  Title,
  Subtitle,
  Body,
  Link,
  SubHeading,
  ListTitle1,
  ListTitle2,
  ListSubtitle,
  NoteTitle,
}

object TextTypeDefaults {
  @Composable
  fun appearance(
    type: TextType,
    enabled: Boolean = true,
  ): TextAppearance {
    val typography = MeTheme.typography

    return when (type) {
      TextType.Title ->
        TextAppearance(
          style = typography.heading4,
          color = colorScheme.textHeading,
        )

      TextType.Subtitle ->
        TextAppearance(
          style = typography.subHeading1,
          color = if (enabled) colorScheme.textBody else colorScheme.utility,
        )

      TextType.Body ->
        TextAppearance(
          style = typography.body2,
          color = if (enabled) colorScheme.textBody else colorScheme.utility,
        )

      TextType.Link ->
        TextAppearance(
          style = typography.link1,
          color = colorScheme.primaryAction,
        )

      TextType.SubHeading ->
        TextAppearance(
          style = typography.body3,
          color = colorScheme.textSubheading,
        )

      TextType.ListTitle1 ->
        TextAppearance(
          style = typography.heading5,
          color = colorScheme.textHeading,
        )

      TextType.ListTitle2 ->
        TextAppearance(
          style = typography.heading4,
          color = colorScheme.textHeading,
        )

      TextType.ListSubtitle ->
        TextAppearance(
          style = typography.subHeading2,
          color = colorScheme.textSubheading,
        )

      TextType.NoteTitle ->
        TextAppearance(
          style = typography.heading6,
          color = colorScheme.textHeading,
        )
    }
  }
}

@Composable
fun AppText(
  text: String = "",
  textType: TextType,
  modifier: Modifier = Modifier,
  annotatedText: String? = null,
  annotationPosition: AnnotationPosition = AnnotationPosition.Start,
  spanStyle: SpanStyle? = null,
  enabled: Boolean = true,
  spacing: Dp = LocalSpacing.current.none,
  textAlign: TextAlign = TextAlign.Start,
  color: Color? = null,
  onClick: (() -> Unit)? = null,
  onAnnotationClick: ((String) -> Unit)? = null,
) {
  val appearance = TextTypeDefaults.appearance(textType, enabled)

  val ANNOTATION_TAG = "ANNOTATED_CLICK"

  val finalText = remember(text, annotatedText, spanStyle) {
    if (annotatedText != null && spanStyle != null) {
      buildAnnotatedString {
        when (annotationPosition) {
          AnnotationPosition.Start -> {
            pushStringAnnotation(tag = ANNOTATION_TAG, annotation = annotatedText)
            withStyle(spanStyle) { append(annotatedText) }
            pop()
            append(text.removePrefix(annotatedText))
          }

          AnnotationPosition.End -> {
            append(text.removeSuffix(annotatedText))
            pushStringAnnotation(tag = ANNOTATION_TAG, annotation = annotatedText)
            withStyle(spanStyle) { append(annotatedText) }
            pop()
          }

          AnnotationPosition.Middle -> {
            val startIndex = text.indexOf(annotatedText)
            if (startIndex != -1) {
              append(text.substring(0, startIndex))
              pushStringAnnotation(tag = ANNOTATION_TAG, annotation = annotatedText)
              withStyle(spanStyle) { append(annotatedText) }
              pop()
              append(text.substring(startIndex + annotatedText.length))
            } else {
              append(text) // fallback
            }
          }
        }
      }
    } else {
      buildAnnotatedString {
        text.split(" ").forEachIndexed { index, word ->
          val isUppercase = word.any { it.isLetter() } && word == word.uppercase()
          if (isUppercase) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
              append(word)
            }
          } else {
            append(word)
          }
          if (index != text.lastIndex) append(" ")
        }
      }
    }
  }

  Column(
    modifier = Modifier.wrapContentSize(),
    horizontalAlignment = Alignment.Start,
  ) {
    if (onAnnotationClick != null && annotatedText != null && spanStyle != null) {
      ClickableText(
        text = finalText,
        style = appearance.style.copy(color = color ?: appearance.color, textAlign = textAlign),
        modifier = modifier,
        onClick = { offset ->
          finalText.getStringAnnotations(
            tag = ANNOTATION_TAG,
            start = offset,
            end = offset + 1,
          ).firstOrNull()
            ?.let { annotation ->
              onAnnotationClick(annotation.item)
            }
        },
      )
    } else {
      Text(
        text = finalText,
        style = appearance.style,
        color = color ?: appearance.color,
        textAlign = textAlign,
        modifier =
          modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier,
          ),
      )
    }
    Spacer(modifier = Modifier.height(spacing))
  }
}

@PreviewTheme
@Composable
fun AppTextPreview() {
  MeAppTheme {
    Column {
      AppText("Title", TextType.Title)
      AppText("Sub title", TextType.Subtitle)
      AppText("Body", TextType.Body)
    }
  }
}
