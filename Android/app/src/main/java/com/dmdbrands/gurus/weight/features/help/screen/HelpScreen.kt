package com.dmdbrands.gurus.weight.features.help.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.ScaleList
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.components.VersionText
import com.dmdbrands.gurus.weight.features.help.model.HelpIntent
import com.dmdbrands.gurus.weight.features.help.strings.HelpScreenStrings
import com.dmdbrands.gurus.weight.features.help.viewmodel.HelpViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.delay
import java.util.Locale.getDefault
import android.content.Intent

/**
 * Help screen composable. Displays segmented control for different help sections and related content.
 */
@Composable
fun HelpScreen() {
  val viewModel: HelpViewModel = hiltViewModel()

  BackHandler {
    viewModel.handleIntent(HelpIntent.OnBack)
  }

  HelpContent(viewModel, viewModel::handleIntent)
}

/**
 * Main content composable for the Help screen.
 */
@Composable
private fun HelpContent(
  viewModel: HelpViewModel,
  handleIntent: (HelpIntent) -> Unit
) {

  // Debug tap tracking (like Angular tryToOpenDebugModal)
  var debugTaps by remember { mutableIntStateOf(0) }
  LaunchedEffect(debugTaps) {
    if (debugTaps > 0) {
      delay(10000)
      debugTaps = 0
    }
  }
  AppScaffold(
    title = HelpScreenStrings.Title,
    enable = true,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        handleIntent(HelpIntent.OnBack)
      }
    },
    appBarOnclick = {
      debugTaps++
      if (debugTaps >= 5) {
        viewModel.onOpenDebugMenu()
        debugTaps = 0 // Reset after opening debug menu
      }
    },
  ) { scaffoldModifier ->
    ScaleList(
      modifier = scaffoldModifier
        .fillMaxSize(),
      onScaleSelected = { scale ->
        val url = "${AppConfig.PRODUCT_URL}/${scale.sku}"
        handleIntent(HelpIntent.OpenUrl(url))
      },
      header = {
        ContactUsContent(handleIntent)
        Spacer(Modifier.padding(bottom = MeTheme.spacing.xl))
      },
      footer = {
        AppVersionContent()
      }
    )
  }
}

/**
 * Contact Us content composable.
 */
@Composable
private fun ContactUsContent(handleIntent: (HelpIntent) -> Unit) {
  val context = LocalContext.current

  Column(
    horizontalAlignment = Alignment.Start,
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = MeTheme.spacing.sm, end = MeTheme.spacing.sm, top = MeTheme.spacing.md),
  ) {
    AppText(
      text = HelpScreenStrings.ContactSectionTitle,
      textType = TextType.Title,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.padding(top = MeTheme.spacing.x3s))
    AppText(
      text = HelpScreenStrings.ContactSectionSubtitle,
      textType = TextType.Subtitle,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
    // Phone contact
    val phoneNumber = HelpScreenStrings.Phone
    AppText(
      text = phoneNumber,
      textType = TextType.Link,
      textAlign = TextAlign.Start,
      modifier = Modifier.clickable(
        true,
        onClick = {
          val intent = Intent(Intent.ACTION_DIAL).apply {
            data = "tel:$phoneNumber".toUri()
          }
          context.startActivity(intent)
        },
      ),
    )
    Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.sm))
    AppText(
      text = HelpScreenStrings.Email.uppercase(getDefault()),
      textType = TextType.Link,
      textAlign = TextAlign.Start,
      modifier = Modifier.clickable(true) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
          data = "mailto:${HelpScreenStrings.Email}".toUri()
        }
        context.startActivity(intent)
      },
    )
    Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      AppText(
        text = HelpScreenStrings.UserManualSectionTitle,
        textType = TextType.Title,
      )
      AppIconButton(
        AppIcons.Outlined.Help,
        iconSize = 20.dp,
      ) {
        handleIntent(HelpIntent.ShowModelNumberHelpPopup)
      }
    }
    AppText(
      text = HelpScreenStrings.UserManualSectionSubtitle,
      textType = TextType.Subtitle,
    )
  }
}

/**
 * Footer content composable for the Help screen.
 */
@Composable
private fun AppVersionContent() {
  Column(
    horizontalAlignment = Alignment.Start,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = MeTheme.spacing.xl),
  ) {
    VersionText(
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center,
      style = MeTheme.typography.subHeading2,
      titlePrefix = "App"
    )
  }
}

@PreviewTheme
@Composable
private fun HelpScreenPreview() {
  MeAppTheme {
    // HelpContent(
    //     handleIntent = {},
    // )
  }
}
