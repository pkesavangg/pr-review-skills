package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import android.util.Log

/**
 * Enum defining the visual style of the chip component.
 */
enum class ChipType {
    Solid,
    /** Solid chip with filled background */
}

/**
 * Enum defining the size variants of the chip component.
 */
enum class ChipSize {
    Small,

    /** Small chip size */
    Medium,

    /** Medium chip size */
    Large,
    /** Large chip size */
}

/**
 * Default styling configurations for the chip component.
 */
object ChipButtonDefaults {
    /**
     * Returns the background color for the chip based on type, selection, and enabled state.
     */
    @Composable
    fun backgroundColor(type: ChipType): Color =
        when (type) {
            ChipType.Solid -> MeTheme.colorScheme.primaryBackground
        }

    /**
     * Returns the content color for the chip based on type, selection, and enabled state.
     */
    @Composable
    fun contentColor(type: ChipType): Color =
        when (type) {
            ChipType.Solid -> MeTheme.colorScheme.primaryAction
        }

    /**
     * Returns the border stroke for the chip. Only outlined chips show border when selected.
     */
    @Composable
    fun border(
        type: ChipType,
        selected: Boolean,
    ): BorderStroke? =
        when (type) {
            ChipType.Solid ->
                BorderStroke(
                    1.5.dp,
                    if (selected) MeTheme.colorScheme.primaryAction else Color.Transparent,
                )
            // TODO: val borderInDp = with(LocalDensity.current) { 1.5f.toDp() }
        }

    /**
     * Returns the horizontal padding based on chip size.
     */
    @Composable
    fun horizontalPadding(size: ChipSize): Dp =
        when (size) {
            ChipSize.Small -> MeTheme.spacing.sm
            ChipSize.Medium -> MeTheme.spacing.lg
            ChipSize.Large -> MeTheme.spacing.xl
        }

    /**
     * Returns the vertical padding based on chip size.
     */
    @Composable
    fun verticalPadding(size: ChipSize): Dp =
        when (size) {
            ChipSize.Small -> MeTheme.spacing.xs
            ChipSize.Medium -> MeTheme.spacing.sm
            ChipSize.Large -> MeTheme.spacing.lg
        }

    /**
     * Returns the text style based on chip size.
     */
    @Composable
    fun textStyle(size: ChipSize): TextStyle =
        when (size) {
            ChipSize.Small -> MeTheme.typography.link1
            ChipSize.Medium -> MeTheme.typography.button1
            ChipSize.Large -> MeTheme.typography.button1
        }

    /**
     * Returns the corner radius based on chip size.
     */
    @Composable
    fun cornerRadius(size: ChipSize): Dp =
        when (size) {
            ChipSize.Small -> MeTheme.borderRadius.xs
            ChipSize.Medium -> MeTheme.borderRadius.sm
            ChipSize.Large -> MeTheme.borderRadius.md
        }

    /**
     * Returns the height based on chip size.
     */
    fun height(size: ChipSize): Dp =
        when (size) {
            ChipSize.Small -> 38.dp
            ChipSize.Medium -> 40.dp
            ChipSize.Large -> 42.dp
        }

    /**
     * Returns the height based on chip size.
     */
    fun minWidth(size: ChipSize): Dp =
        when (size) {
            ChipSize.Small -> 80.dp
            ChipSize.Medium -> 40.dp
            ChipSize.Large -> 42.dp
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
 * A reusable chip component that supports different visual styles and states.
 *
 * @param label The text to display in the chip
 * @param modifier Modifier to be applied to the chip
 * @param type The visual style of the chip (Solid or Outlined)
 * @param size The size variant of the chip
 * @param selected Whether the chip is currently selected
 * @param onClick Callback invoked when the chip is clicked
 *
 */
@Composable
fun AppChip(
    label: String,
    modifier: Modifier = Modifier,
    type: ChipType = ChipType.Solid,
    size: ChipSize = ChipSize.Small,
    enabled: Boolean = true,
    selected: Boolean = false,
    textTransform: TextTransform = TextTransform.UPPERCASE,
    onClick: () -> Unit,
) {
    val backgroundColor = ChipButtonDefaults.backgroundColor(type)
    val contentColor = ChipButtonDefaults.contentColor(type)
    val border = ChipButtonDefaults.border(type, selected)
    val hPadding = ChipButtonDefaults.horizontalPadding(size)
    val vPadding = ChipButtonDefaults.verticalPadding(size)
    val textStyle = ChipButtonDefaults.textStyle(size)
    val cornerRadius = ChipButtonDefaults.cornerRadius(size)
    val shape = RoundedCornerShape(cornerRadius)
    val height = ChipButtonDefaults.height(size)
    val minWidth = ChipButtonDefaults.minWidth(size)
    val text = ChipButtonDefaults.transformText(label, textTransform)
    val chipModifier =
        modifier
            .height(height)
            .defaultMinSize(minWidth = minWidth)
    val maxLines = 1
    val density = LocalDensity.current

    FilterChip(
        shape = shape,
        colors =
            SelectableChipColors(
                containerColor = backgroundColor,
                disabledContainerColor = backgroundColor,
                selectedContainerColor = backgroundColor,
                labelColor = contentColor,
                leadingIconColor = contentColor,
                trailingIconColor = contentColor,
                disabledLabelColor = contentColor,
                disabledLeadingIconColor = contentColor,
                disabledTrailingIconColor = contentColor,
                disabledSelectedContainerColor = contentColor,
                selectedLabelColor = contentColor,
                selectedLeadingIconColor = contentColor,
                selectedTrailingIconColor = contentColor,
            ),
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        label = {
            Text(
                text,
                style = textStyle,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(horizontal = hPadding, vertical = vPadding)
                    .onSizeChanged {
                        Log.i("CHECKING", with(density) { it.width.toDp() }.toString())
                    },
                maxLines = maxLines,
            )
        },
        selected = selected,
        leadingIcon = {},
        border = border,
        modifier = chipModifier,
    )
}

@PreviewTheme
@Composable
fun SolidChipsPreviewLight() {
    MeAppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var isSelected1 by remember { mutableStateOf(false) }
            var isSelected2 by remember { mutableStateOf(false) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppChip(
                    label = "Unselected",
                    onClick = { isSelected1 = !isSelected1 },
                    type = ChipType.Solid,
                    selected = isSelected1,
                )
                AppChip(
                    label = "Selected",
                    onClick = { isSelected2 = !isSelected2 },
                    type = ChipType.Solid,
                    selected = isSelected2,
                )
            }
        }
    }
}
