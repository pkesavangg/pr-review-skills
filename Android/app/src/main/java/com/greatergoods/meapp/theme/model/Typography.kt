package com.greatergoods.meapp.theme.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextStyle

@Stable
data class Typography(
    val heading1: TextStyle,
    val heading2: TextStyle,
    val heading3: TextStyle,
    val heading4: TextStyle,
    val heading5: TextStyle,

    val subHeading1: TextStyle,
    val subHeading2: TextStyle,

    val body1: TextStyle,
    val body2: TextStyle,
    val body3: TextStyle,

    val link1: TextStyle,
    val link2: TextStyle,

    val button1: TextStyle,
    val button2: TextStyle
)
