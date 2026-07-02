package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.WifiMacAddressStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

@Composable
fun WifiMacAddress(
  title: String,
  macAddress: String,
  onCopyMacAddress: (Boolean) -> Unit,
) {
  val context = LocalContext.current

  Column(
    modifier =
      Modifier
        .fillMaxSize().verticalScroll(rememberScrollState())
        .padding(vertical = spacing.md, horizontal = spacing.sm),
    verticalArrangement = Arrangement.spacedBy(spacing.lg),
  ) {

    AppText(
      text = title,
      textType = TextType.Title,
      modifier = Modifier.fillMaxWidth(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
      AppNote(
        message = macAddress,
        messageType = TextType.Message,
        isCenter = true
      )

      AppButton(
        label = WifiMacAddressStrings.CopyMacButton,
        type = ButtonType.TextPrimary,
        size = ButtonSize.Medium,
        onClick = {
          try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val macAddress = macAddress
            val clip = ClipData.newPlainText("MAC Address", macAddress)
            clipboard.setPrimaryClip(clip)
            onCopyMacAddress(true)
          } catch (e: Exception) {
            onCopyMacAddress(false)
          }
        },
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }


    AppText(
      text = WifiMacAddressStrings.MacAddressNote,
      textType = TextType.Body,
    )
  }
}

@PreviewTheme
@Composable
fun WifiMacAddressPreview() {
  MeAppTheme {
    WifiMacAddress(
      title = WifiMacAddressStrings.Title,
      macAddress = "AA:BB:CC:DD:EE:FF",
      onCopyMacAddress = {},
    )
  }
}
