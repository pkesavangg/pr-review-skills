package com.greatergoods.meapp.features.sample

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.ui.shared.components.AppButton
import com.greatergoods.meapp.ui.shared.components.ButtonSize
import com.greatergoods.meapp.ui.shared.components.ButtonType

@Composable
fun ButtonVariants(){
    AppButton(
        type = ButtonType.PrimaryFilled,
        text = "PrimaryFilled",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )

    AppButton(
        type = ButtonType.PrimaryOutlined,
        text = "PrimaryOutlined",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )

    AppButton(
        type = ButtonType.SecondaryFilled,
        text = "SecondaryFilled",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )

    AppButton(
        type = ButtonType.SecondaryOutlined,
        text = "SecondaryOutlined",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )

    AppButton(
        type = ButtonType.TextPrimary,
        text = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    AppButton(
        type = ButtonType.TextSecondary,
        text = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )

    AppButton(
        type = ButtonType.InlineText,
        text = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )

}
