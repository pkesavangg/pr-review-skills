package com.greatergoods.meapp.features.scaleDetails.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppNote
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsIntent
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsState
import com.greatergoods.meapp.features.scaleDetails.strings.WifiMacAddressStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

@Composable
fun WifiMacAddressScreen(
  state: ScaleDetailsState,
  handleIntent: (ScaleDetailsIntent) -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current

  BackHandler {
    onClose()
  }
  AppScaffold(
    title = WifiMacAddressStrings.Header,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close, onClick = onClose)
    },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(vertical = spacing.md, horizontal = spacing.sm),
      verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {

      AppText(
        text = WifiMacAddressStrings.Title,
        textType = TextType.Title,
        modifier = Modifier.fillMaxWidth(),
      )

      Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        AppNote(
          message = WifiMacAddressStrings.MacEncryption,
        )
        AppButton(
          label = WifiMacAddressStrings.CopyMacButton,
          type = ButtonType.TextPrimary,
          size = ButtonSize.Large,
          onClick = {
            try {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val macAddress = state.scale?.device?.macAddress ?: throw IllegalStateException("MAC Address is null")
              val clip = ClipData.newPlainText("MAC Address", macAddress)
              clipboard.setPrimaryClip(clip)
              handleIntent(ScaleDetailsIntent.OnCopyMacAddress(true))
            } catch (e: Exception) {
              handleIntent(ScaleDetailsIntent.OnCopyMacAddress(false))
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
}

@PreviewTheme
@Composable
fun WifiMacAddressScreenPreview() {
  MeAppTheme {
  }
}
