package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@Composable
fun MEImage(
    lightMode: Int,
    darkMode: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val image = if (isSystemInDarkTheme()) darkMode else lightMode
    Image(
        painter = painterResource(id = image),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

