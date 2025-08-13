package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.WifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SelectButtonGrid
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.SelectButtonHelper
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonItem
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun  SelectButton(
  title: String,
  modifier: Modifier = Modifier,
  selectButtonItems: List<SelectButtonItem>,
  subtitle: String? = null,
  noteMessage: String? = null,
  isSelectable: Boolean = false,
  isAPMode: Boolean = false,
  supportingButtonLabel: String? = null,
  onItemSelected: ((String) -> Unit)? = null,
  content: (@Composable () -> Unit)? = null,
  onSupportingButtonClick: (() -> Unit)? = null,
  sku: String? = null,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(vertical = spacing.md)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(spacing.lg),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
      AppText(
        text = title,
        textType = TextType.Title,
        modifier = Modifier.fillMaxWidth(),
      )
      subtitle?.let {
        AppText(
          text = subtitle,
          textType = TextType.Body,
          canApplyUppercaseStyle = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    // Select Buttons Grid
    selectButtonItems.let { items ->
      SelectButtonGrid(
        items = items,
        isSelectable = isSelectable,
        onItemSelected = onItemSelected,
        modifier = Modifier.fillMaxWidth(),
        sku = sku,
      )
    }

    noteMessage?.let {
      AppNote(
        message = noteMessage,
        showNote = true,
      )
    }

    if (supportingButtonLabel != null && onSupportingButtonClick != null) {
      AppButton(
        label = supportingButtonLabel,
        type = ButtonType.InlineTextPrimary,
        onClick = onSupportingButtonClick,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }
    content?.let {
      content()
    }

  }
}

// @PreviewTheme
// @Composable
// private fun SelectButtonWithUserNumbersPreview() {
//   MeAppTheme {
//     val userNumbers = (1..8).toList()
//     var selectedUser by remember { mutableStateOf<Int?>(0) }
//     val userButtons = SelectButtonHelper.createUserNumberButtons(userNumbers, selectedNumber = selectedUser)
//
//     SelectButton(
//       title = WifiScaleSetupStrings.ChooseUser.Title,
//       subtitle = WifiScaleSetupStrings.ChooseUser.Message,
//       selectButtonItems = userButtons,
//       isSelectable = true,
//       onItemSelected = { value ->
//         // Handle user selection
//         selectedUser = value.toInt()
//       },
//     )
//   }
// }

@PreviewTheme
@Composable
private fun SelectButtonWithWifiModesPreview() {
  MeAppTheme {
    var selectedMode by remember { mutableStateOf<String?>(null) }
    val wifiButtons = SelectButtonHelper.createWifiModeButtons(selectedMode = "espTouchWifi")

    SelectButton(
      title = WifiScaleSetupStrings.WifiMode.Title,
      selectButtonItems = wifiButtons,
      isSelectable = true,
      onItemSelected = { value ->
        selectedMode = value
        // Handle wifi mode selection
      },
      noteMessage = WifiScaleSetupStrings.WifiMode.ApNote,
      supportingButtonLabel = "hello",
      onSupportingButtonClick = {},
    )
  }
}

// @PreviewTheme
// @Composable
// private fun SelectButtonWithErrorCodesPreview() {
//   MeAppTheme {
//     var selectedErrorCode by remember { mutableStateOf<String?>(null) }
//
//     val errorButtons = SelectButtonHelper.createDefaultErrorCodeButtons(selectedErrorCode = selectedErrorCode)
//
//     SelectButton(
//       title = WifiScaleSetupStrings.Error.Title,
//       subtitle = WifiScaleSetupStrings.Error.Message,
//       selectButtonItems = errorButtons,
//       isSelectable = true,
//       onItemSelected = { value ->
//         selectedErrorCode = value
//         // Handle error code selection
//       },
//       supportingButtonLabel = ScaleSetupStrings.SetupButtons.SomethingElse,
//       onSupportingButtonClick = {},
//     )
//   }
// }
