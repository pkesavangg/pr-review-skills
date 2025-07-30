package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme

@Composable
fun MEImage(
    lightMode: Int,
    darkMode: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    val image = if (isSystemInDarkTheme()) darkMode else lightMode
    Image(
        painter = painterResource(id = image),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

@PreviewTheme
@Composable
fun MEImagePreview() {
    MeAppTheme {
        MEImage(lightMode = AppIcons.Default.Logo, darkMode = AppIcons.Default.Logo, contentDescription = "MeApp Logo")
    }
}
