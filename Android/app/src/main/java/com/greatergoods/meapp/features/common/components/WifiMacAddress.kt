package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.greatergoods.meapp.features.scaleDetails.strings.WifiMacAddressStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing
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
        .fillMaxSize()
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
        message = WifiMacAddressStrings.MacEncryption,
        messageType = TextType.Message
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
