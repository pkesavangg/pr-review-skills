package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.features.common.model.Loader
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.exposeTestTagsAsResourceId
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

@Composable
fun LoaderCard(loader: Loader) {
  Dialog(
    onDismissRequest = { },
    properties = DialogProperties(
      dismissOnClickOutside = false,
      dismissOnBackPress = false,
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .exposeTestTagsAsResourceId()
        .background(colorScheme.overlay),
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.inverseAction),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.align(Alignment.Center),
      ) {
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
}

@PreviewTheme
@Composable
fun LoaderCardTheme() {
  MeAppTheme {
    LoaderCard(loader = Loader(message = "Loading"))
  }
}
