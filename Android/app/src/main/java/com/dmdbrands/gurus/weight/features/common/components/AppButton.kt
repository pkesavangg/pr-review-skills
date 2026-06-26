// AppButton.kt
// This file defines a customizable button component for Jetpack Compose with various styles and sizes.

package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.isPhoneLike
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
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
  SuccessFilled
}

/**
 * True for the three `InlineText*` variants and `ErrorText`. Inline-text buttons
 * opt out of fixed height and horizontal padding so they sit flush in surrounding
 * text. Centralising the predicate ensures any future inline variant only needs to
 * be added here — no `==` chain at call sites can fall out of sync.
 */
val ButtonType.isInlineText: Boolean
  get() = this == ButtonType.InlineTextPrimary ||
    this == ButtonType.InlineTextSecondary ||
    this == ButtonType.InlineTextTertiary ||
    this == ButtonType.ErrorText

// Color
// Type - outline/filled/text
// style - block/inline

// Button size options
enum class ButtonSize { XSmall, Small, Medium, Large }

// Text transformation options
enum class TextTransform { NONE, UPPERCASE, LOWERCASE, CAPITALIZE }

// Default values and helpers for AppButton
object AppButtonDefaults {
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
        if (enabled) MeTheme.colorScheme.primaryAction else MeTheme.colorScheme.primaryActionDisabled

      ButtonType.SecondaryFilled ->
        if (enabled) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.inverseActionDisabled

      ButtonType.PrimaryOutlined ->
        if (enabled) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.inverseActionDisabled

      ButtonType.SecondaryOutlined ->
        if (enabled) MeTheme.colorScheme.primaryAction else MeTheme.colorScheme.primaryActionDisabled
      ButtonType.SuccessFilled ->
        if(enabled)  MeTheme.colorScheme.success else MeTheme.colorScheme.tertiarySuccess

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
      ButtonType.PrimaryFilled, ButtonType.SecondaryOutlined, ButtonType.TextSecondary, ButtonType.InlineTextSecondary, ButtonType.SuccessFilled ->
        if (enabled) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.inverseActionDisabled

      ButtonType.SecondaryFilled, ButtonType.PrimaryOutlined, ButtonType.TextPrimary, ButtonType.InlineTextPrimary ->
        if (enabled) MeTheme.colorScheme.primaryAction else MeTheme.colorScheme.primaryActionDisabled

      ButtonType.TextTertiary, ButtonType.InlineTextTertiary ->
        if (enabled) MeTheme.colorScheme.tertiaryAction else MeTheme.colorScheme.tertiaryActionDisabled

      ButtonType.ErrorText ->
        if (enabled) MeTheme.colorScheme.errorAction else MeTheme.colorScheme.errorActionDisabled
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
          if (enabled) MeTheme.colorScheme.primaryAction else MeTheme.colorScheme.primaryActionDisabled,
        )

      ButtonType.SecondaryOutlined ->
        BorderStroke(
          2.dp,
          if (enabled) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.inverseActionDisabled,
        )

      else -> null
    }

  /**
   * Returns pressed background color based on button type.
   */
  @Composable
  fun pressedBackgroundColor(type: ButtonType): Color =
    when (type) {
      ButtonType.PrimaryFilled -> MeTheme.colorScheme.primaryFocusedAction
      ButtonType.SecondaryFilled -> MeTheme.colorScheme.inverseActionSecondary
      ButtonType.PrimaryOutlined -> MeTheme.colorScheme.inverseActionSecondary
      ButtonType.SecondaryOutlined -> MeTheme.colorScheme.primaryFocusedAction
      ButtonType.SuccessFilled -> MeTheme.colorScheme.success
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
          MeTheme.colorScheme.primaryFocusedAction,
        )

      ButtonType.SecondaryOutlined ->
        BorderStroke(
          2.dp,
          MeTheme.colorScheme.inverseActionSecondary,
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
        MeTheme.colorScheme.primaryFocusedAction

      ButtonType.TextSecondary, ButtonType.InlineTextSecondary ->
        MeTheme.colorScheme.inverseActionSecondary

      ButtonType.TextTertiary, ButtonType.InlineTextTertiary -> MeTheme.colorScheme.tertiaryActionSecondary
      ButtonType.ErrorText -> MeTheme.colorScheme.errorAction
      ButtonType.PrimaryFilled, ButtonType.SuccessFilled, ButtonType.SecondaryOutlined -> if (enabled) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.inverseActionDisabled
      ButtonType.SecondaryFilled, ButtonType.PrimaryOutlined -> if (enabled) MeTheme.colorScheme.primaryAction else MeTheme.colorScheme.primaryActionDisabled
    }

  // Button height by size
  fun height(size: ButtonSize): Dp =
    when (size) {
      ButtonSize.XSmall -> 16.dp
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
    if (type.isInlineText) {
      return 0.dp
    }
    return when (size) {
      ButtonSize.XSmall -> MeTheme.spacing.xs
      ButtonSize.Small -> MeTheme.spacing.sm
      ButtonSize.Medium -> MeTheme.spacing.md
      ButtonSize.Large -> MeTheme.spacing.lg
    }
  }

  // Minimum width by size
  fun minWidth(size: ButtonSize): Dp =
    when (size) {
      ButtonSize.XSmall -> 60.dp
      ButtonSize.Small -> 80.dp
      ButtonSize.Medium -> 130.dp
      ButtonSize.Large -> 160.dp
    }

  // Text style by size
  @Composable
  fun textStyle(size: ButtonSize): TextStyle =
    when (size) {
      ButtonSize.Large, ButtonSize.Medium -> MeTheme.typography.button1
      ButtonSize.Small, ButtonSize.XSmall -> MeTheme.typography.button2
    }

  // Smallest font the label may shrink to before the button gives up width.
  // Lets long labels (or large system font scales) fit on one line instead of
  // truncating, e.g. "App Permission" clipping to "App" (MOB-174).
  // This is an intentional readable layout floor (not a theme token): kept close
  // to the design sizes (button1 16sp / button2 14sp) so a user who deliberately
  // chose a large system font scale never has the label shrunk below a legible
  // size — shrinking past this would itself be an accessibility regression
  // (WCAG 1.4.4). The 0.5sp step is the auto-size granularity, also a layout
  // constant rather than a typographic token.
  private val MIN_AUTO_FONT_SIZE = 12.sp
  private val AUTO_FONT_STEP = 0.5.sp

  /**
   * Auto-size config that caps the label at the design font size for [size] and
   * shrinks it down to [MIN_AUTO_FONT_SIZE] when the available width is tight.
   * Caps at the design size so default-scale buttons render unchanged.
   */
  fun autoSize(textStyle: TextStyle): TextAutoSize =
    TextAutoSize.StepBased(
      minFontSize = MIN_AUTO_FONT_SIZE,
      maxFontSize = textStyle.fontSize,
      stepSize = AUTO_FONT_STEP,
    )

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
  throttleTime: Long = 700L,
  onClick: () -> Unit,
): () -> Unit {
  var lastEmitTime by remember { mutableStateOf(0L) }
  val onClickState = rememberUpdatedState(onClick)

  return remember {
    {
      val currentTime = android.os.SystemClock.elapsedRealtime()
      if (currentTime - lastEmitTime >= throttleTime) {
        lastEmitTime = currentTime
        onClickState.value()
      }
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
fun AppButton(
  label: String,
  modifier: Modifier = Modifier,
  type: ButtonType = ButtonType.PrimaryFilled,
  size: ButtonSize = ButtonSize.Large,
  enabled: Boolean = true,
  textTransform: TextTransform = TextTransform.UPPERCASE,
  onClick: () -> Unit,
) {
  // Get style values from defaults
  val backgroundColor = AppButtonDefaults.backgroundColor(type, enabled)
  val contentColor = AppButtonDefaults.contentColor(type, enabled)
  AppButtonDefaults.pressedBackgroundColor(type)
  AppButtonDefaults.pressedContentColor(type, enabled)
  val focusedBackgroundColor = AppButtonDefaults.pressedBackgroundColor(type)
  val focusedContentColor = AppButtonDefaults.pressedContentColor(type, enabled)
  AppButtonDefaults.pressedBorder(type)
  val border = AppButtonDefaults.border(type, enabled)
  val pressedBorder = AppButtonDefaults.pressedBorder(type)
  val height = AppButtonDefaults.height(size)
  val hPadding = AppButtonDefaults.horizontalPadding(size, type)
  val textStyle = AppButtonDefaults.textStyle(size)
  // Stable across recompositions — only rebuilt when the design font size changes.
  val autoSize = remember(textStyle.fontSize) { AppButtonDefaults.autoSize(textStyle) }
  val text = AppButtonDefaults.transformText(label, textTransform)
  val minWidth = AppButtonDefaults.minWidth(size)
  val shape = RoundedCornerShape(50)
  val vPadding = 0.dp
  val maxLines = 1
  // Phones / folded displays keep pixel-parity fixed height; tablets use
  // heightIn so the button can grow with the label instead of clipping the
  // text under tablet density (MA-3713).
  val isPhoneLike = getDeviceType().isPhoneLike
  val buttonModifier = modifier
    .then(
      when {
        type.isInlineText -> Modifier
        isPhoneLike -> Modifier.height(height)
        else -> Modifier.heightIn(min = height)
      },
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

  val throttledClick = rememberThrottledClick { onClick() }

  // For transparent buttons, use a custom implementation to avoid ripple effects
  if (finalBackgroundColor == Color.Transparent) {
    Box(
      modifier = buttonModifier
        .clip(shape)
        .background(
          color = finalBackgroundColor,
          shape = shape
        )
        .clickable(
          enabled = enabled,
          interactionSource = interactionSource,
          onClick = throttledClick
        )
        // TalkBack: the Material3 Button branch announces "<label>, button" automatically.
        // This transparent Box branch needs the Button role added explicitly, and
        // mergeDescendants so the child label folds into the button node (otherwise
        // TalkBack reads a nameless "button" and the label as a separate focus stop).
        .semantics(mergeDescendants = true) { role = Role.Button },
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = text,
        style = textStyle,
        color = finalContentColor,
        maxLines = maxLines,
        autoSize = autoSize,
        modifier = Modifier.padding(
          horizontal = hPadding,
          vertical = vPadding
        )
      )
    }
  } else {
    // Use Material3 Button for non-transparent backgrounds
    val buttonColors = ButtonDefaults.buttonColors(
      containerColor = finalBackgroundColor,
      contentColor = finalContentColor,
      disabledContainerColor = backgroundColor,
      disabledContentColor = contentColor,
    )

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
      Text(text = text, style = textStyle, maxLines = maxLines, autoSize = autoSize)
    }
  }
}

// --- Preview Section ---
// Shows all button types and sizes for design review.
@PreviewTheme
@Composable
fun AppButtonPreview() {
  MeAppTheme {
    Row(Modifier.padding(20.dp)) {
      // Left column: Medium buttons
      Column(verticalArrangement = Arrangement.Center) {
        // Each group: enabled/disabled for each type
        Column {
          AppButton(type = ButtonType.ErrorText, onClick = {}, label = "Primary Filled", enabled = true)
          Spacer(Modifier.height(16.dp))
          AppButton(type = ButtonType.PrimaryFilled, onClick = {}, label = "Primary Filled", enabled = false)
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.SecondaryFilled,
            label = "Secondary Filled",
            enabled = true,
          ) {
            // Button click handler
          }
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.SecondaryFilled,
            onClick = {},
            label = "Secondary Filled",
            enabled = false,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.PrimaryOutlined, onClick = {}, label = "Primary Outlined")
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.PrimaryOutlined,
            onClick = {},
            label = "Primary Outlined",
            enabled = false,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.SecondaryOutlined, onClick = {}, label = "Secondary Outlined")
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.SecondaryOutlined,
            onClick = {},
            label = "Secondary Outlined",
            enabled = false,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.TextPrimary, onClick = {}, label = "Text Primary")
          Spacer(Modifier.height(16.dp))
          AppButton(type = ButtonType.TextPrimary, onClick = {}, label = "Text Primary", enabled = false)
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.TextSecondary, onClick = {}, label = "Text Primary")
          Spacer(Modifier.height(16.dp))
          AppButton(type = ButtonType.TextSecondary, onClick = {}, label = "Text Primary", enabled = false)
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.TextTertiary, onClick = {}, label = "Text Tertiary")
          Spacer(Modifier.height(16.dp))
          AppButton(type = ButtonType.TextTertiary, onClick = {}, label = "Text Tertiary", enabled = false)
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.InlineTextPrimary, onClick = {}, label = "Inline text Primary")
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.InlineTextPrimary,
            onClick = {},
            label = "Inline text Primary",
            enabled = false,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.InlineTextSecondary, onClick = {}, label = "Inline text Secondary")
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.InlineTextSecondary,
            onClick = {},
            label = "Inline text Secondary",
            enabled = false,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.ErrorText, onClick = {}, label = "Error Text")
          Spacer(Modifier.height(16.dp))
          AppButton(type = ButtonType.ErrorText, onClick = {}, label = "Error Text", enabled = false)
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(type = ButtonType.PrimaryFilled, onClick = {}, label = "Min Size")
        }
      }
      // Right column: Small buttons
      Column(verticalArrangement = Arrangement.Center) {
        Column {
          AppButton(
            type = ButtonType.PrimaryFilled,
            onClick = {},
            label = "Primary Filled",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.PrimaryFilled,
            onClick = {},
            label = "Primary Filled",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.SecondaryFilled,
            onClick = {},
            label = "Secondary Filled",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.SecondaryFilled,
            onClick = {},
            label = "Secondary Filled",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.PrimaryOutlined,
            onClick = {},
            label = "Primary Outlined",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.PrimaryOutlined,
            onClick = {},
            label = "Primary Outlined",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.SecondaryOutlined,
            onClick = {},
            label = "Secondary Outlined",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.SecondaryOutlined,
            onClick = {},
            label = "Secondary Outlined",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.TextPrimary,
            onClick = {},
            label = "Text Primary",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.TextPrimary,
            onClick = {},
            label = "Text Primary",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.TextSecondary,
            onClick = {},
            label = "Text Primary",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.TextSecondary,
            onClick = {},
            label = "Text Primary",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.TextTertiary,
            onClick = {},
            label = "Text Tertiary",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.TextTertiary,
            onClick = {},
            label = "Text Tertiary",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.InlineTextPrimary,
            onClick = {},
            label = "Inline text Primary",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
            type = ButtonType.InlineTextPrimary,
            onClick = {},
            label = "Inline text Primary",
            enabled = false,
            size = ButtonSize.Small,
          )
        }
        Spacer(Modifier.height(16.dp))
        Column {
          AppButton(
            type = ButtonType.InlineTextSecondary,
            onClick = {},
            label = "Inline text Secondary",
            size = ButtonSize.Small,
          )
          Spacer(Modifier.height(16.dp))
          AppButton(
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
            AppButton(
              type = ButtonType.ErrorText,
              onClick = {},
              label = "Error Text",
              size = ButtonSize.Small,
            )
            Spacer(Modifier.height(16.dp))
            AppButton(
              type = ButtonType.ErrorText,
              onClick = {},
              label = "Error Text",
              enabled = false,
              size = ButtonSize.Small,
            )
          }
          Spacer(Modifier.height(16.dp))
          Column {
            AppButton(type = ButtonType.PrimaryFilled, onClick = {}, label = "Min", size = ButtonSize.Small)
          }
        }
      }
    }
  }
}
