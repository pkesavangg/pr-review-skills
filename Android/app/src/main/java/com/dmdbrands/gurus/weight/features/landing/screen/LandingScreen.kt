package com.dmdbrands.gurus.weight.features.landing.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.VersionText
import com.dmdbrands.gurus.weight.features.landing.strings.LandingString
import com.dmdbrands.gurus.weight.features.loading.string.LoadingString
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch
import android.app.Activity

@Composable
fun LandingScreen() {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  context as? Activity

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MeTheme.colorScheme.primaryAction),
  ) {
    // Main content
    Column(
      modifier =
        Modifier
          .align(Alignment.Center)
          .padding(horizontal = MeTheme.spacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Image(
        painter = painterResource(id = AppIcons.Default.Banner),
        contentDescription = LoadingString.LOADING,
        colorFilter = ColorFilter.tint(MeTheme.colorScheme.inverseAction),
      )
      Spacer(modifier = Modifier.height(MeTheme.spacing.x6l))
      AppButton(
        type = ButtonType.SecondaryFilled,
        label = LandingString.Login,
        size = ButtonSize.Large,
        onClick = {
          coroutineScope.launch {
            backStack.addRoute(
              AppRoute.Auth.Login(),
            )
          }
        },
      )
      Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      AppButton(
        type = ButtonType.SecondaryOutlined,
        label = LandingString.SignUp,
        size = ButtonSize.Large,
        onClick = {
          coroutineScope.launch {
            backStack.addRoute(
              AppRoute.Auth.Signup,
            )
          }
        },
      )
    }
    // Footer
    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(bottom = MeTheme.spacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
    ) {
      VersionText(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MeTheme.colorScheme.inverseAction,
        style = MeTheme.typography.subHeading2,
      )
    }
  }
}

@PreviewTheme
@Composable
fun LoginScreenPreviewLight() {
  MeAppTheme {
    LandingScreen()
  }
}
