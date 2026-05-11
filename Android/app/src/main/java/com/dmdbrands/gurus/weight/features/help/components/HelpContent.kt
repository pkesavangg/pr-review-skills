package com.dmdbrands.gurus.weight.features.help.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.components.VersionText
import com.dmdbrands.gurus.weight.features.help.model.HelpIntent
import com.dmdbrands.gurus.weight.features.help.strings.HelpScreenStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import java.util.Locale.getDefault

@Composable
fun ContactUsContent(handleIntent: (HelpIntent) -> Unit) {
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

@Composable
fun AppVersionContent() {
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
