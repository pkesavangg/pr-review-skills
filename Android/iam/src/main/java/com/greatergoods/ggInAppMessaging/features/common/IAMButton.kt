package com.greatergoods.ggInAppMessaging.features.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.theme.IamTheme
import kotlinx.coroutines.flow.collectLatest

// Button style types
enum class ButtonType {
  PrimaryFilled,
  SecondaryFilled,
  PrimaryOutlined,
  SecondaryOutlined,
  TextPrimary,
  TextSecondary,
  TextTertiary,
  InlineTextPrimary,
  InlineTextSecondary,
  InlineTextTertiary,
  ErrorText,
}

// Color
// Type - outline/filled/text
// style - block/inline

// Button size options
enum class ButtonSize { Small, Medium, Large }

// Text transformation options
enum class TextTransform { NONE, UPPERCASE, LOWERCASE, CAPITALIZE }

// Default values and helpers for AppButton
object IamButtonDefaults {
  /**
   * Returns background color based on button type and enabled state.
   */
  @Composable
  fun backgroundColor(
    type: ButtonType,
    enabled: Boolean,
  ): Color =
    when (type) {
      ButtonType.PrimaryFilled ->
        if (enabled) IamTheme.colors.primaryAction else IamTheme.colors.primaryActionDisabled

      ButtonType.SecondaryFilled ->
        if (enabled) IamTheme.colors.inverseAction else IamTheme.colors.inverseActionDisabled

      ButtonType.PrimaryOutlined ->
        if (enabled) IamTheme.colors.inverseAction else IamTheme.colors.inverseActionDisabled

      ButtonType.SecondaryOutlined ->
        if (enabled) IamTheme.colors.primaryAction else IamTheme.colors.primaryActionDisabled

      else -> Color.Transparent
    }

  /**
   * Returns content (text/icon) color based on button type and enabled state.
   */
  @Composable
  fun contentColor(
    type: ButtonType,
    enabled: Boolean,
  ): Color =
    when (type) {
      ButtonType.PrimaryFilled, ButtonType.SecondaryOutlined, ButtonType.TextSecondary, ButtonType.InlineTextSecondary ->
        if (enabled) IamTheme.colors.inverseAction else IamTheme.colors.inverseActionDisabled

      ButtonType.SecondaryFilled, ButtonType.PrimaryOutlined, ButtonType.TextPrimary, ButtonType.InlineTextPrimary ->
        if (enabled) IamTheme.colors.primaryAction else IamTheme.colors.primaryActionDisabled

      ButtonType.TextTertiary, ButtonType.InlineTextTertiary ->
        if (enabled) IamTheme.colors.tertiaryAction else IamTheme.colors.tertiaryActionDisabled

      ButtonType.ErrorText ->
        if (enabled) IamTheme.colors.errorAction else IamTheme.colors.errorActionDisabled
    }

  /**
   * Returns border for outlined buttons, or null otherwise.
   */
  @Composable
  fun border(
    type: ButtonType,
    enabled: Boolean,
  ): BorderStroke? =
    when (type) {
      ButtonType.PrimaryOutlined ->
        BorderStroke(
          2.dp,
          if (enabled) IamTheme.colors.primaryAction else IamTheme.colors.primaryActionDisabled,
        )

      ButtonType.SecondaryOutlined ->
        BorderStroke(
          2.dp,
          if (enabled) IamTheme.colors.inverseAction else IamTheme.colors.inverseActionDisabled,
        )

      else -> null
    }

  /**
   * Returns pressed background color based on button type.
   */
  @Composable
  fun pressedBackgroundColor(type: ButtonType): Color =
    when (type) {
      ButtonType.PrimaryFilled -> IamTheme.colors.primaryFocusedAction
      ButtonType.SecondaryFilled -> IamTheme.colors.inverseActionSecondary
      ButtonType.PrimaryOutlined -> IamTheme.colors.inverseActionSecondary
      ButtonType.SecondaryOutlined -> IamTheme.colors.primaryFocusedAction
      else -> Color.Transparent
    }

  /**
   * Returns pressed background color based on button type.
   */
  @Composable
  fun pressedBorder(type: ButtonType): BorderStroke? =
    when (type) {
      ButtonType.PrimaryOutlined ->
        BorderStroke(
          2.dp,
          IamTheme.colors.primaryFocusedAction,
        )

      ButtonType.SecondaryOutlined ->
        BorderStroke(
          2.dp,
          IamTheme.colors.inverseActionSecondary,
        )

      else -> null
    }

  /**
   * Returns pressed content color based on button type.
   */
  @Composable
  fun pressedContentColor(type: ButtonType, enabled: Boolean): Color =
    when (type) {
      ButtonType.TextPrimary, ButtonType.InlineTextPrimary ->
        IamTheme.colors.primaryFocusedAction

      ButtonType.TextSecondary, ButtonType.InlineTextSecondary ->
        IamTheme.colors.inverseActionSecondary

      ButtonType.TextTertiary, ButtonType.InlineTextTertiary -> IamTheme.colors.tertiaryActionSecondary
      ButtonType.ErrorText -> IamTheme.colors.errorAction
      ButtonType.PrimaryFilled, ButtonType.SecondaryOutlined -> if (enabled) IamTheme.colors.inverseAction else IamTheme.colors.inverseActionDisabled
      ButtonType.SecondaryFilled, ButtonType.PrimaryOutlined -> if (enabled) IamTheme.colors.primaryAction else IamTheme.colors.primaryActionDisabled
    }

  // Button height by size
  fun height(size: ButtonSize): Dp =
    when (size) {
      ButtonSize.Small -> 35.dp
      ButtonSize.Medium -> 45.dp
      ButtonSize.Large -> 40.dp
    }

  // Horizontal padding by size
  @Composable
  fun horizontalPadding(
    size: ButtonSize,
    type: ButtonType,

    ): Dp {
    if (type == ButtonType.InlineTextPrimary || type == ButtonType.InlineTextSecondary) {
      return 0.dp
    }
    return when (size) {
      ButtonSize.Small -> 16.dp
      ButtonSize.Medium -> 24.dp
      ButtonSize.Large -> 32.dp
    }
  }

  // Minimum width by size
  fun minWidth(size: ButtonSize): Dp =
    when (size) {
      ButtonSize.Small -> 80.dp
      ButtonSize.Medium -> 130.dp
      ButtonSize.Large -> 160.dp
    }

  // Text style by size
  @Composable
  fun textStyle(size: ButtonSize): TextStyle =
    when (size) {
      ButtonSize.Large, ButtonSize.Medium -> IamTheme.typography.button1
      ButtonSize.Small -> IamTheme.typography.button2
    }

  // Applies text transformation
  fun transformText(
    text: String,
    transform: TextTransform,
  ): String =
    when (transform) {
      TextTransform.UPPERCASE -> text.uppercase()
      TextTransform.LOWERCASE -> text.lowercase()
      TextTransform.CAPITALIZE -> text.replaceFirstChar { it.uppercase() }
      TextTransform.NONE -> text
    }
}

/**
 * Returns a throttled click lambda that implements RxJS throttleTime-like behavior.
 * Emits immediately on first click, then throttles subsequent clicks for [throttleTime].
 */
@Composable
private fun rememberThrottledClick(
  throttleTime: Long = 500L,
  onClick: () -> Unit,
): () -> Unit {
  // Persist across recompositions and process death safe defaults
  var lastEmitTime by rememberSaveable { mutableStateOf(0L) }
  val onClickState = rememberUpdatedState(onClick)

  return {
    // Use monotonic clock to avoid time changes affecting logic
    val currentTime = android.os.SystemClock.elapsedRealtime()
    if (currentTime - lastEmitTime >= throttleTime) {
      lastEmitTime = currentTime
      onClickState.value()
    }
  }
}

/**
 * A customizable button for the app, supporting various styles and sizes.
 * @param label The button text
 * @param modifier Modifier for styling
 * @param type Button style
 * @param size Button size
 * @param enabled Whether the button is enabled
 * @param textTransform Text transformation
 * @param onClick Click handler
 */
@Composable
fun IamButton(
  label: String,
  modifier: Modifier = Modifier,
  type: ButtonType = ButtonType.PrimaryFilled,
  size: ButtonSize = ButtonSize.Medium,
  enabled: Boolean = true,
  textTransform: TextTransform = TextTransform.UPPERCASE,
  onClick: () -> Unit,
) {
  // Get style values from defaults
  val backgroundColor = IamButtonDefaults.backgroundColor(type, enabled)
  val contentColor = IamButtonDefaults.contentColor(type, enabled)
  IamButtonDefaults.pressedBackgroundColor(type)
  IamButtonDefaults.pressedContentColor(type, enabled)
  val focusedBackgroundColor = IamButtonDefaults.pressedBackgroundColor(type)
  val focusedContentColor = IamButtonDefaults.pressedContentColor(type, enabled)
  IamButtonDefaults.pressedBorder(type)
  val border = IamButtonDefaults.border(type, enabled)
  val pressedBorder = IamButtonDefaults.pressedBorder(type)
  val height = IamButtonDefaults.height(size)
  val hPadding = IamButtonDefaults.horizontalPadding(size, type)
  val textStyle = IamButtonDefaults.textStyle(size)
  val text = IamButtonDefaults.transformText(label, textTransform)
  val minWidth = IamButtonDefaults.minWidth(size)
  val shape = RoundedCornerShape(50)
  val vPadding = 0.dp
  val maxLines = 1

  val buttonModifier = modifier
    .then(
      Modifier.height(height),
    )
    .defaultMinSize(minWidth = minWidth)

  // Create interaction source for focus state
  val interactionSource = remember { MutableInteractionSource() }
  var isPressed by remember { mutableStateOf(false) }

  // Determine colors based on focus state
  val finalBackgroundColor = if (isPressed) focusedBackgroundColor else backgroundColor
  val finalContentColor = if (isPressed) focusedContentColor else contentColor
  val borderColor = if (isPressed) pressedBorder else border

  // Collect interactions
  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collectLatest { interaction: Interaction ->
      when (interaction) {
        is PressInteraction.Press -> isPressed = true
        is PressInteraction.Release,
        is PressInteraction.Cancel -> isPressed = false
      }
    }
  }

  val buttonColors =
    ButtonDefaults.buttonColors(
      containerColor = finalBackgroundColor,
      contentColor = finalContentColor,
      disabledContainerColor = backgroundColor,
      disabledContentColor = contentColor,
    )

  val throttledClick = rememberThrottledClick { onClick() }

  Button(
    onClick = throttledClick,
    enabled = enabled,
    shape = shape,
    colors = buttonColors,
    border = borderColor,
    modifier = buttonModifier,
    contentPadding = PaddingValues(vertical = vPadding, horizontal = hPadding),
    interactionSource = interactionSource,
  ) {
    Text(text = text, style = textStyle, maxLines = maxLines)
  }
}

// --- Preview Section ---
// Shows all button types and sizes for design review.
@Preview
@Composable
fun AppButtonPreview() {
  Row(Modifier.padding(20.dp)) {
    // Left column: Medium buttons
    Column(verticalArrangement = Arrangement.Center) {
      // Each group: enabled/disabled for each type
      Column {
        IamButton(type = ButtonType.ErrorText, onClick = {}, label = "Primary Filled", enabled = true)
        Spacer(Modifier.height(16.dp))
        IamButton(type = ButtonType.PrimaryFilled, onClick = {}, label = "Primary Filled", enabled = false)
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.SecondaryFilled,
          label = "Secondary Filled",
          enabled = true,
        ) {
          // Button click handler
        }
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.SecondaryFilled,
          onClick = {},
          label = "Secondary Filled",
          enabled = false,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.PrimaryOutlined, onClick = {}, label = "Primary Outlined")
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.PrimaryOutlined,
          onClick = {},
          label = "Primary Outlined",
          enabled = false,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.SecondaryOutlined, onClick = {}, label = "Secondary Outlined")
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.SecondaryOutlined,
          onClick = {},
          label = "Secondary Outlined",
          enabled = false,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.TextPrimary, onClick = {}, label = "Text Primary")
        Spacer(Modifier.height(16.dp))
        IamButton(type = ButtonType.TextPrimary, onClick = {}, label = "Text Primary", enabled = false)
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.TextSecondary, onClick = {}, label = "Text Primary")
        Spacer(Modifier.height(16.dp))
        IamButton(type = ButtonType.TextSecondary, onClick = {}, label = "Text Primary", enabled = false)
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.TextTertiary, onClick = {}, label = "Text Tertiary")
        Spacer(Modifier.height(16.dp))
        IamButton(type = ButtonType.TextTertiary, onClick = {}, label = "Text Tertiary", enabled = false)
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.InlineTextPrimary, onClick = {}, label = "Inline text Primary")
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.InlineTextPrimary,
          onClick = {},
          label = "Inline text Primary",
          enabled = false,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.InlineTextSecondary, onClick = {}, label = "Inline text Secondary")
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.InlineTextSecondary,
          onClick = {},
          label = "Inline text Secondary",
          enabled = false,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.ErrorText, onClick = {}, label = "Error Text")
        Spacer(Modifier.height(16.dp))
        IamButton(type = ButtonType.ErrorText, onClick = {}, label = "Error Text", enabled = false)
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(type = ButtonType.PrimaryFilled, onClick = {}, label = "Min Size")
      }
    }
    // Right column: Small buttons
    Column(verticalArrangement = Arrangement.Center) {
      Column {
        IamButton(
          type = ButtonType.PrimaryFilled,
          onClick = {},
          label = "Primary Filled",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.PrimaryFilled,
          onClick = {},
          label = "Primary Filled",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.SecondaryFilled,
          onClick = {},
          label = "Secondary Filled",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.SecondaryFilled,
          onClick = {},
          label = "Secondary Filled",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.PrimaryOutlined,
          onClick = {},
          label = "Primary Outlined",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.PrimaryOutlined,
          onClick = {},
          label = "Primary Outlined",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.SecondaryOutlined,
          onClick = {},
          label = "Secondary Outlined",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.SecondaryOutlined,
          onClick = {},
          label = "Secondary Outlined",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.TextPrimary,
          onClick = {},
          label = "Text Primary",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.TextPrimary,
          onClick = {},
          label = "Text Primary",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.TextSecondary,
          onClick = {},
          label = "Text Primary",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.TextSecondary,
          onClick = {},
          label = "Text Primary",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.TextTertiary,
          onClick = {},
          label = "Text Tertiary",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.TextTertiary,
          onClick = {},
          label = "Text Tertiary",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.InlineTextPrimary,
          onClick = {},
          label = "Inline text Primary",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.InlineTextPrimary,
          onClick = {},
          label = "Inline text Primary",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        IamButton(
          type = ButtonType.InlineTextSecondary,
          onClick = {},
          label = "Inline text Secondary",
          size = ButtonSize.Small,
        )
        Spacer(Modifier.height(16.dp))
        IamButton(
          type = ButtonType.InlineTextSecondary,
          onClick = {},
          label = "Inline text Secondary",
          enabled = false,
          size = ButtonSize.Small,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column {
        Column {
          IamButton(
            type = ButtonType.ErrorText,
            onClick = {},
            label = "Error Text",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          IamButton(
            type = ButtonType.ErrorText,
            onClick = {},
            label = "Error Text",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          IamButton(type = ButtonType.PrimaryFilled, onClick = {}, label = "Min", size = ButtonSize.Small)
        }
      }
    }
  }
}
