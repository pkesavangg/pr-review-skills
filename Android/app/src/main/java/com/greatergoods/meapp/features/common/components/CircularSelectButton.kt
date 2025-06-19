package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

object CircularButtonDefaults {

    @Composable
    fun containerColor(
        isSelected: Boolean,
        selectedColor: Color? = null,
        unselectedColor: Color = Color.Transparent
    ): Color = if (isSelected) selectedColor ?: MeTheme.colorScheme.primaryAction else unselectedColor

    @Composable
    fun contentColor(
        isSelected: Boolean,
        selectedColor: Color? = null,
        unselectedColor: Color? = null
    ): Color =
        if (isSelected) selectedColor ?: MeTheme.colorScheme.inverseAction
        else unselectedColor ?: MeTheme.colorScheme.primaryAction

    @Composable
    fun borderColor(
        isSelected: Boolean,
        customColor: Color? = null
    ): Color = customColor ?: MeTheme.colorScheme.primaryAction

    fun borderStroke(color: Color): BorderStroke =
        BorderStroke(2.dp, color)

    val defaultSize: Dp = 128.dp

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
 * A circular button that shows a selected state (filled) and unselected state (outlined).
 *
 * @param text The text to display inside the button.
 * @param isSelected Whether the button is currently selected.
 * @param onClick Callback for when the button is clicked.
 * @param modifier Modifier for custom styling.
 */
@Composable
fun CircularSelectButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    textTransform: TextTransform = TextTransform.UPPERCASE,
    selectedBackgroundColor: Color? = null,
    unselectedContentColor: Color? = null,
    borderColor: Color? = null,
    onClick: () -> Unit,
) {
    val containerColor = CircularButtonDefaults.containerColor(isSelected, selectedBackgroundColor)
    val contentColor = CircularButtonDefaults.contentColor(isSelected, selectedColor = null, unselectedColor = unselectedContentColor)
    val resolvedBorderColor = CircularButtonDefaults.borderColor(isSelected, customColor = borderColor)

    val text = CircularButtonDefaults.transformText(text, textTransform)
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = CircularButtonDefaults.borderStroke(resolvedBorderColor),
        modifier = modifier.size(CircularButtonDefaults.defaultSize)
    ) {
        Text(
            text = text,
            style = MeTheme.typography.button1
        )
    }
}


@PreviewTheme
@Composable
private fun CircularSelectButtonSelectedPreview() {
    MeAppTheme {
        Column {
            CircularSelectButton(text = "SELECTED", isSelected = true, onClick = {})
            CircularSelectButton(text = "UNSELECTED", isSelected = false, onClick = {})
        }
    }
}

