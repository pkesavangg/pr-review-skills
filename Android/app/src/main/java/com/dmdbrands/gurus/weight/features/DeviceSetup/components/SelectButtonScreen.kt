package com.dmdbrands.gurus.weight.features.DeviceSetup.components

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.WifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
  imageWidth: Dp? = null,
  imageHeight: Dp? = null,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md),
    verticalArrangement = Arrangement.spacedBy(spacing.lg),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
      AppText(
        text = title,
        textType = TextType.Title,
        // TalkBack: selection-step title is the heading.
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = spacing.sm)
          .semantics { heading() },
      )
      subtitle?.let {
        AppText(
          text = subtitle,
          textType = TextType.Body,
          canApplyUppercaseStyle = true,
          modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm),
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
        imageWidth = imageWidth ?: 150.dp,
        imageHeight = imageHeight ?: 84.dp,
      )
    }

    noteMessage?.let {
      AppNote(
        message = noteMessage,
        showNote = true,
        modifier = Modifier.padding(horizontal = spacing.sm),
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

