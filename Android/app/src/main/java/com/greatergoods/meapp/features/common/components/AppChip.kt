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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Enum defining the visual style of the chip component.
 */
enum class ChipType {
    Solid, /** Solid chip with filled background */
}

/**
 * Enum defining the size variants of the chip component.
 */
enum class ChipSize {
    Small,    /** Small chip size */
    Medium,    /** Medium chip size */
    Large    /** Large chip size */
}

/**
 * Default styling configurations for the chip component.
 */
object CustomChipDefaults {

    /**
     * Returns the background color for the chip based on type, selection, and enabled state.
     */
    @Composable
    fun backgroundColor(
        type: ChipType,
    ): Color = when (type) {
        ChipType.Solid ->  MeAppTheme.colorScheme.primary
    }

    /**
     * Returns the content color for the chip based on type, selection, and enabled state.
     */
    @Composable
    fun contentColor(
        type: ChipType,
    ): Color = when (type) {
        ChipType.Solid -> MeAppTheme.colorScheme.primaryAction
    }

    /**
     * Returns the border stroke for the chip. Only outlined chips show border when selected.
     */
    @Composable
    fun border(
        type: ChipType,
        selected: Boolean
    ): BorderStroke? = when (type) {
        ChipType.Solid -> BorderStroke(1.5.dp, if(selected) MeAppTheme.colorScheme.primaryAction else MeAppTheme.colorScheme.primary)
           // TODO: val borderInDp = with(LocalDensity.current) { 1.5f.toDp() }
    }

    /**
     * Returns the horizontal padding based on chip size.
     */
    @Composable
    fun horizontalPadding(size: ChipSize): Dp = when (size) {
        ChipSize.Small -> MeAppTheme.spacing.sm
        ChipSize.Medium ->  MeAppTheme.spacing.lg
        ChipSize.Large ->  MeAppTheme.spacing.xl
    }

    /**
     * Returns the vertical padding based on chip size.
     */
    @Composable
    fun verticalPadding(size: ChipSize): Dp = when (size) {
        ChipSize.Small -> MeAppTheme.spacing.xs
        ChipSize.Medium -> MeAppTheme.spacing.sm
        ChipSize.Large -> MeAppTheme.spacing.lg
    }

    /**
     * Returns the text style based on chip size.
     */
    @Composable
    fun textStyle(size: ChipSize): TextStyle = when (size) {
        ChipSize.Small -> MeAppTheme.typography.button1
        ChipSize.Medium -> MeAppTheme.typography.button1
        ChipSize.Large -> MeAppTheme.typography.button1
    }

    /**
     * Returns the corner radius based on chip size.
     */
    fun cornerRadius(size: ChipSize): Dp = when (size) {
        ChipSize.Small -> 4.dp
        ChipSize.Medium -> 8.dp
        ChipSize.Large -> 12.dp
        //TODO: Need to update after pr merged
    }

    /**
     * Returns the height based on chip size.
     */
    fun height(size: ChipSize): Dp = when (size) {
        ChipSize.Small -> 38.dp
        ChipSize.Medium -> 40.dp
        ChipSize.Large -> 42.dp
    }

    /**
     * Returns the height based on chip size.
     */
    fun minWidth(size: ChipSize): Dp = when (size) {
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
    selected: Boolean = false,
    textTransform: TextTransform = TextTransform.CAPITALIZE,
    onClick: () -> Unit,
) {
    val backgroundColor = CustomChipDefaults.backgroundColor(type)
    val contentColor = CustomChipDefaults.contentColor(type)
    val border = CustomChipDefaults.border(type,selected)
    val hPadding = CustomChipDefaults.horizontalPadding(size)
    val vPadding = CustomChipDefaults.verticalPadding(size)
    val textStyle = CustomChipDefaults.textStyle(size)
    val cornerRadius = CustomChipDefaults.cornerRadius(size)
    val shape = RoundedCornerShape(cornerRadius)
    val height = CustomChipDefaults.height(size)
    val minWidth = CustomChipDefaults.minWidth(size)
    val text = CustomChipDefaults.transformText(label, textTransform)
    val chipModifier = modifier.height(height).defaultMinSize(minWidth = minWidth)

    FilterChip(
        shape = shape,
        colors = SelectableChipColors(
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
        onClick = onClick,
        label = {
            Text(text, style = textStyle, modifier = Modifier.padding(horizontal = hPadding, vertical = vPadding))
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
    MeAppTheme() {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var isSelected1 by remember { mutableStateOf(false) }
            var isSelected2 by remember { mutableStateOf(false) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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


