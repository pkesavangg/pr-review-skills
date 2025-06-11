package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
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

enum class TextTransform {
    NONE,
    UPPERCASE,
    LOWERCASE,
    CAPITALIZE
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
        ButtonSize.Medium -> 40.dp
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

    fun transformText(text: String, transform: TextTransform): String {
        return when (transform) {
            TextTransform.UPPERCASE -> text.uppercase()
            TextTransform.LOWERCASE -> text.lowercase()
            TextTransform.CAPITALIZE -> text.replaceFirstChar { it.uppercase() }
            TextTransform.NONE -> text
        }
    }

}

//Design system now have only uppercase for button label
@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.PrimaryFilled,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = false,
    textTransform: TextTransform = TextTransform.UPPERCASE
) {
    val backgroundColor = CustomButtonDefaults.backgroundColor(type, enabled)
    val contentColor = CustomButtonDefaults.contentColor(type, enabled)
    val border = CustomButtonDefaults.border(type, enabled)
    val height = CustomButtonDefaults.height(size)
    val hPadding = CustomButtonDefaults.horizontalPadding(size)
    val textStyle = CustomButtonDefaults.textStyle(size)
    val text = CustomButtonDefaults.transformText(label, textTransform)

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

@PreviewTheme
@Composable
fun PrimaryFilledPreview(){
    MeAppTheme {
        Column(
            modifier = Modifier.padding(50.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppButton(
                type = ButtonType.PrimaryFilled,
                onClick = { /* Do something */ },
                label = "Primary Filled",
                enabled = true
            )
        }
    }
}

@PreviewTheme
@Composable
fun PrimaryOutlinedPreview(){
    MeAppTheme {
        Column(
            modifier = Modifier.padding(50.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppButton(
                type = ButtonType.PrimaryOutlined,
                onClick = { /* Do something */ },
                label = "PrimaryOutlined",
                enabled = true
            )
        }
    }
}

@PreviewTheme
@Composable
fun SecondaryFilledPreview(){
    MeAppTheme {
        Column(
            modifier = Modifier.padding(50.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppButton(
                type = ButtonType.SecondaryFilled,
                onClick = { /* Do something */ },
                label = "SecondaryFilled",
                enabled = true
            )
        }
    }
}

@PreviewTheme
@Composable
fun SecondaryOutlinedPreview(){
    MeAppTheme {
        Column(
            modifier = Modifier.padding(50.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppButton(
                type = ButtonType.SecondaryOutlined,
                onClick = { /* Do something */ },
                label = "SecondaryOutlined",
                enabled = true
            )
        }
    }
}

@PreviewTheme
@Composable
fun TextPrimaryPreview(){
    MeAppTheme {
        Column(
            modifier = Modifier.padding(50.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppButton(
                type = ButtonType.TextPrimary,
                onClick = { /* Do something */ },
                label = "TextPrimary",
                enabled = true
            )
        }
    }
}

@PreviewTheme
@Composable
fun TextSecondaryPreview(){
    MeAppTheme {
        Column(
            modifier = Modifier.padding(50.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppButton(
                type = ButtonType.TextSecondary,
                onClick = { /* Do something */ },
                label = "TextPrimary",
                enabled = true
            )
        }
    }
}

@PreviewTheme
@Composable
fun InlineTextPreview() {
    MeAppTheme {
        Column(
            modifier = Modifier.padding(50.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppButton(
                type = ButtonType.InlineText,
                onClick = { /* Do something */ },
                label = "Inline text",
                enabled = true,
                textTransform = TextTransform.CAPITALIZE
            )
        }
    }
}



