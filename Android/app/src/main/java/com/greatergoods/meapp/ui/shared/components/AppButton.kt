package com.greatergoods.meapp.ui.shared.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.theme.MeAppTheme

//here link Primary is text button and link Secondary is text button
enum class ButtonType {
    PrimaryFilled,
    PrimaryOutlined,
    SecondaryFilled,
    SecondaryOutlined,
    TextPrimary,
    TextSecondary,
    InlineText,
}

enum class ButtonSize {
    Small,
    Medium,
    Large
}

object CustomButtonDefaults {
    // Example colors; replace with your real theme tokens!
    @Composable
    fun backgroundColor(type: ButtonType, enabled: Boolean): Color =
        when (type) {
            ButtonType.PrimaryFilled -> if (enabled) MeAppTheme.colorScheme.primaryAction else MeAppTheme.colorScheme.primaryDisabled
            ButtonType.PrimaryOutlined -> if (enabled) MeAppTheme.colorScheme.inverse else MeAppTheme.colorScheme.inverseDisabled
            ButtonType.TextPrimary -> Color.Transparent
            ButtonType.TextSecondary -> Color.Transparent
            ButtonType.InlineText -> Color.Transparent
            ButtonType.SecondaryFilled -> if (enabled) MeAppTheme.colorScheme.primaryAction else MeAppTheme.colorScheme.primaryDisabled
            ButtonType.SecondaryOutlined -> if (enabled) MeAppTheme.colorScheme.inverse else MeAppTheme.colorScheme.inverseDisabled
        }

    @Composable
    fun contentColor(type: ButtonType, enabled: Boolean): Color =
        when (type) {
            ButtonType.PrimaryFilled -> if (enabled) MeAppTheme.colorScheme.inverse else MeAppTheme.colorScheme.inverseDisabled
            ButtonType.PrimaryOutlined -> if (enabled) MeAppTheme.colorScheme.primaryAction else MeAppTheme.colorScheme.primaryDisabled
            ButtonType.TextPrimary -> if (enabled) MeAppTheme.colorScheme.primaryAction else MeAppTheme.colorScheme.primaryDisabled
            ButtonType.TextSecondary -> if (enabled) MeAppTheme.colorScheme.tertiaryAction else MeAppTheme.colorScheme.tertiaryDisabled
            ButtonType.InlineText -> if(enabled) MeAppTheme.colorScheme.primaryAction else MeAppTheme.colorScheme.primaryDisabled
            ButtonType.SecondaryFilled -> if(enabled) MeAppTheme.colorScheme.inverse else MeAppTheme.colorScheme.inverseDisabled
            ButtonType.SecondaryOutlined -> if(enabled) MeAppTheme.colorScheme.primaryAction else MeAppTheme.colorScheme.primaryDisabled
        }

    @Composable
    fun border(type: ButtonType, enabled: Boolean): BorderStroke? =
        when (type) {
             ButtonType.PrimaryOutlined -> BorderStroke(1.dp,
                if (enabled) MeAppTheme.colorScheme.primaryAction else MeAppTheme.colorScheme.primaryDisabled
            )
            ButtonType.SecondaryFilled  ->  BorderStroke(1.dp,
                if (enabled) MeAppTheme.colorScheme.inverse else MeAppTheme.colorScheme.inverseDisabled
            )
            ButtonType.SecondaryOutlined,ButtonType.PrimaryFilled, ButtonType.TextPrimary, ButtonType.TextSecondary,ButtonType.InlineText -> null
        }

    fun height(size: ButtonSize): Dp = when (size) {
        ButtonSize.Small -> 30.dp
        ButtonSize.Medium -> 48.dp
        ButtonSize.Large -> 48.dp
    }


    fun horizontalPadding(size: ButtonSize): Dp = when (size) {
        ButtonSize.Small -> 16.dp
        ButtonSize.Medium -> 32.dp
        ButtonSize.Large -> 48.dp
    }

    @Composable
    fun textStyle(size: ButtonSize): TextStyle = when (size) {
        ButtonSize.Large -> MeAppTheme.typography.button1
        ButtonSize.Medium -> MeAppTheme.typography.button1
        ButtonSize.Small -> MeAppTheme.typography.button2
    }
}


@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.PrimaryFilled,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = false,
) {
    val backgroundColor = CustomButtonDefaults.backgroundColor(type, enabled)
    val contentColor = CustomButtonDefaults.contentColor(type, enabled)
    val border = CustomButtonDefaults.border(type, enabled)
    val height = CustomButtonDefaults.height(size)
    val hPadding = CustomButtonDefaults.horizontalPadding(size)
    val textStyle = CustomButtonDefaults.textStyle(size)

    val shape = RoundedCornerShape(24.dp)

    val buttonModifier = modifier
        .height(height)
        .defaultMinSize(minWidth = 150.dp)
        .padding(horizontal = hPadding)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = backgroundColor,
            disabledContentColor = contentColor,
        ),
        border = border,
        modifier = buttonModifier
    ) {
        Text(text = text, style = textStyle)
    }
}

@Preview(name = "Light Theme Preview", showBackground = true)
@Composable
fun AllButtonsPreviewLight() {
    MeAppTheme(themeMode = ThemeMode.DARK) { // Provide the Light theme
        AppButton(
            type = ButtonType.PrimaryOutlined,
            onClick = { /* Do something */ },
            text = "Light Button",
            enabled = true
        )
    }
}

@Preview(name = "Dark Theme Preview", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AllButtonsPreviewDark() {
    MeAppTheme(themeMode = ThemeMode.DARK) { // Provide the Dark theme
        AppButton(
            type = ButtonType.PrimaryFilled,
            onClick = { /* Do something */ },
            text = "Dark Button",
            enabled = true
        )
    }
}

@Preview(name = "System Theme Preview", showBackground = true)
@Composable
fun AllButtonsPreviewSystem() {
    // No explicit themeMode needed here, it will default to SYSTEM and use isSystemInDarkTheme()
    MeAppTheme {
        AppButton(
            onClick = { /* Do something */ },
            text = "System Button",
            enabled = true
        )
    }
}


