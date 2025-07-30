package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dmdbrands.gurus.weight.features.common.model.Loader
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

@Composable
fun LoaderCard(loader: Loader) {
    Dialog(onDismissRequest = { }) {
        Card(colors = CardDefaults.cardColors(containerColor = colorScheme.inverseAction), shape = RoundedCornerShape(16.dp)) {
            AppLoader(
                modifier =
                    Modifier
                        .padding(vertical = 32.dp, horizontal = 40.dp),
                message = loader.message,
                isLoading = true,
                style = loader.style,
            )
        }
    }
}

@PreviewTheme
@Composable
fun LoaderCardTheme() {
    MeAppTheme {
        LoaderCard(loader = Loader(message = "Loading"))
    }
}
