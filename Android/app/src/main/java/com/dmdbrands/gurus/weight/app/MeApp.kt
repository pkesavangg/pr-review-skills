package com.dmdbrands.gurus.weight.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.app.components.NavHost
import com.dmdbrands.gurus.weight.app.viewmodel.AppIntent
import com.dmdbrands.gurus.weight.app.viewmodel.AppViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.DialogHost
import com.dmdbrands.gurus.weight.features.common.components.ScaleDiscoveredModal
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.token.DarkColorToken
import com.dmdbrands.gurus.weight.theme.token.LightColorToken
import com.example.nav3integration.rememberTopLevelBackStack
import com.greatergoods.ggInAppMessaging.theme.ColorTokens
import com.greatergoods.ggInAppMessaging.theme.IAMConfiguration
import kotlinx.coroutines.delay
import android.util.Log

/**
 * Creates ColorTokens for IAM package based on current app theme
 */
private fun createIAMColorTokens(isDark: Boolean): ColorTokens {
  Log.d("thememodetoken", "${isDark}")
  return if (isDark) {
    ColorTokens(
      // Background
      primaryBackground = Color.Red,
      primaryBackgroundDisabled = DarkColorToken.primaryDisabled,
      secondaryBackground = DarkColorToken.secondary,

      // Support
      overlay = DarkColorToken.overlay,
      toastBackground = DarkColorToken.toastBackground,

      // Action
      primaryFocusedAction = DarkColorToken.primaryFocusedAction,
      primaryAction = DarkColorToken.primaryAction,
      primaryActionDisabled = DarkColorToken.primaryActionDisabled,
      secondaryAction = DarkColorToken.secondaryAction,
      secondaryActionDisabled = DarkColorToken.secondaryActionDisabled,
      tertiaryAction = DarkColorToken.tertiaryAction,
      tertiaryActionDisabled = DarkColorToken.tertiaryActionDisabled,
      tertiaryActionSecondary = DarkColorToken.tertiaryActionSecondary,
      inverseAction = DarkColorToken.inverse,
      inverseActionDisabled = DarkColorToken.inverseDisabled,
      inverseActionSecondary = DarkColorToken.inverseSecondary,
      errorAction = DarkColorToken.errorAction,
      errorActionDisabled = DarkColorToken.errorActionDisabled,
      errorActionSecondary = DarkColorToken.errorActionSecondary,

      // Status
      goal = DarkColorToken.success,
      success = DarkColorToken.success,
      danger = DarkColorToken.danger,
      streak = DarkColorToken.streak,
      utility = DarkColorToken.utility,
      glow = DarkColorToken.glow,

      // Icon
      iconPrimary = DarkColorToken.iconPrimary,
      iconPrimaryDisabled = DarkColorToken.iconPrimaryDisabled,
      iconSecondary = DarkColorToken.iconSecondary,
      iconSecondaryDisabled = DarkColorToken.iconSecondaryDisabled,
      // Text
      textHeading = DarkColorToken.heading,
      textBody = DarkColorToken.body,
      textSubheading = DarkColorToken.subheading,
      textError = DarkColorToken.textError,
      textErrorDisabled = DarkColorToken.textErrorDisabled,

      // Loading
      loading = DarkColorToken.loading,
      loadingError = DarkColorToken.loadingError,

      // Brand
      meAppPrimary = DarkColorToken.meAppPrimary,
      brandWgPrimary = DarkColorToken.wgPrimary,
    )
  } else {
    ColorTokens(
      // Background
      primaryBackground = Color.Green,
      primaryBackgroundDisabled = LightColorToken.primaryDisabled,
      secondaryBackground = LightColorToken.secondary,

      // Status
      goal = LightColorToken.success,
      success = LightColorToken.success,
      danger = LightColorToken.danger,
      streak = LightColorToken.streak,
      utility = LightColorToken.utility,
      glow = LightColorToken.glow,

      // Text
      textHeading = LightColorToken.heading,
      textBody = LightColorToken.body,
      textSubheading = LightColorToken.subheading,
      textError = LightColorToken.textError,
      textErrorDisabled = LightColorToken.textErrorDisabled,

      // Action
      primaryFocusedAction = LightColorToken.primaryFocusedAction,
      primaryAction = LightColorToken.primaryAction,
      primaryActionDisabled = LightColorToken.primaryActionDisabled,
      secondaryAction = LightColorToken.secondaryAction,
      secondaryActionDisabled = LightColorToken.secondaryActionDisabled,
      tertiaryAction = LightColorToken.tertiaryAction,
      tertiaryActionDisabled = LightColorToken.tertiaryActionDisabled,
      tertiaryActionSecondary = LightColorToken.tertiaryActionSecondary,
      inverseAction = LightColorToken.inverse,
      inverseActionDisabled = LightColorToken.inverseDisabled,
      inverseActionSecondary = LightColorToken.inverseSecondary,
      errorAction = LightColorToken.errorAction,
      errorActionDisabled = LightColorToken.errorActionDisabled,
      errorActionSecondary = LightColorToken.errorActionSecondary,

      // Icon
      iconPrimary = LightColorToken.iconPrimary,
      iconPrimaryDisabled = LightColorToken.iconPrimaryDisabled,
      iconSecondary = LightColorToken.iconSecondary,
      iconSecondaryDisabled = LightColorToken.iconSecondaryDisabled,

      // Loading
      loading = LightColorToken.loading,
      loadingError = LightColorToken.loadingError,

      // Support
      overlay = LightColorToken.overlay,
      toastBackground = LightColorToken.toastBackground,

      // Brand
      meAppPrimary = LightColorToken.meAppPrimary,
      brandWgPrimary = LightColorToken.wgPrimary,
    )
  }
}

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 * Handles window insets properly for the entire app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeApp() {
  val appViewModel: AppViewModel = hiltViewModel()
  val uiState by appViewModel.state.collectAsState()

  // Keep navigation stack stable - don't let it be affected by theme changes
  val topLevelBackStack =
    rememberTopLevelBackStack(
      Pair(AppRoute.App, AppRoute.Init.Loading),
      AppRoute.Auth.Login(),
      Pair(AppRoute.Home, AppRoute.Main.Dashboard),
    )

  MeAppTheme(themeMode = uiState.themeMode) {
    val isSystemDark = isSystemInDarkTheme()
    LaunchedEffect(uiState.themeMode, isSystemDark) {
      val isDark = when (uiState.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.UNRECOGNIZED -> isSystemDark
      }
      val iamColorTokens = createIAMColorTokens(isDark)
      IAMConfiguration.updateColors(iamColorTokens)
    }


    Surface(
      modifier =
        Modifier
          .fillMaxSize()
          .imePadding(),
      color = colorScheme.primaryBackground,
    ) {
      Log.d("thememodere", "reload")
      CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
        DialogHost()
        NavHost(topLevelBackStack, appViewModel)
      }
    }
    if (uiState.isScaleDiscovered && uiState.hasScanStarted) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      LaunchedEffect(Unit) {
        delay(15 * 1000)
        appViewModel.handleIntent(AppIntent.OnPopUpDismiss)
      }
      ModalBottomSheet(
        sheetState = sheetState,
        modifier = Modifier.navigationBarsPadding(),
        onDismissRequest = { appViewModel.handleIntent(AppIntent.OnPopUpDismiss) },
        containerColor = colorScheme.primaryBackground,
      ) {
        ScaleDiscoveredModal(sku = uiState.sku) {
          appViewModel.handleIntent(AppIntent.OnPopUpConnect)
        }
      }
    }
  }
}
