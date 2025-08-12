package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.helper.ErrorImageHelper
import com.dmdbrands.gurus.weight.features.common.helper.SelectButtonHelper
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonDisplayValue
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonItem
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography

// Shared dimensions to keep Image and GIF items visually identical
private val SelectItemWidth = 150.dp
private val SelectItemHeight = 84.dp

/**
 * A grid of selectable circular buttons that can display text/numbers or images.
 *
 * @param items List of selectable button items
 * @param isSelectable Whether the buttons are selectable (default: false)
 * @param onItemSelected Callback when an item is selected, emits the selected value
 * @param modifier Modifier for custom styling
 */
@Composable
fun SelectButtonGrid(
  items: List<SelectButtonItem>,
  isSelectable: Boolean = false,
  onItemSelected: ((String) -> Unit)? = null,
  sku: String? = null,
  modifier: Modifier = Modifier,
) {
  val maxColumns = 3
  val rows = items.chunked(maxColumns)

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(spacing.md),
  ) {
    rows.forEach { row ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterHorizontally),
      ) {
        row.forEach { item ->
          SelectButtonItem(
            item = item,
            isSelectable = isSelectable,
            onItemSelected = onItemSelected,
            sku = sku,
          )
        }
      }
    }
  }
}

/**
 * Individual selectable button item.
 *
 * @param item The button data
 * @param isSelectable Whether the button is selectable
 * @param onItemSelected Callback when the item is selected
 * @param modifier Modifier for custom styling
 */
@Composable
private fun SelectButtonItem(
  item: SelectButtonItem,
  isSelectable: Boolean,
  onItemSelected: ((String) -> Unit)?,
  modifier: Modifier = Modifier,
  sku: String? = null,
) {
  val isSelected = item.isSelected && isSelectable
  val backgroundColor = if (isSelected) colorScheme.iconPrimary else colorScheme.inverseAction
  val borderColor = colorScheme.iconPrimary
  val contentColor = if (isSelected) colorScheme.inverseAction else colorScheme.iconPrimary
  when (val displayValue = item.displayValue) {
    is SelectButtonDisplayValue.Text -> {
      Box(
        modifier = modifier
          .size(100.dp)
          .clip(CircleShape)
          .background(backgroundColor)
          .border(
            width = 2.dp,
            color = borderColor,
            shape = CircleShape,
          )
          .clickable(enabled = isSelectable) {
            onItemSelected?.invoke(item.emitValue)
          }
          .padding(spacing.sm),
        contentAlignment = Alignment.Center,
      ) {
        val displayText = if (displayValue.prefix.isNotEmpty()) {
          "${displayValue.prefix}${displayValue.text}"
        } else {
          displayValue.text
        }
        Text(
          text = displayText,
          color = contentColor,
          style = typography.button1,
        )
      }
    }

    is SelectButtonDisplayValue.Image -> {
      // Invisible container ensures consistent size and tap target
      Box(
        modifier = modifier
          .size(width = SelectItemWidth, height = SelectItemHeight)
          .clipToBounds()
          .clickable(
            enabled = isSelectable,
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
          ) { onItemSelected?.invoke(item.emitValue) },
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = painterResource(id = displayValue.imageResId),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit,
        )
      }
    }

    is SelectButtonDisplayValue.Gif -> {
      // Invisible container ensures consistent size and tap target
      Box(
        modifier = modifier
          .size(width = SelectItemWidth, height = SelectItemHeight)
          .clipToBounds()
          .clickable(
            enabled = isSelectable,
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
          ) { onItemSelected?.invoke(item.emitValue) },
        contentAlignment = Alignment.Center,
      ) {
        AppGifImage(
          id = displayValue.imageResId,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }

    is SelectButtonDisplayValue.ErrorCode -> {
      val errorImageResId: Int? =
        if (sku == "0384") {
          ErrorImageHelper.getErrorImageDrawableRectangle(displayValue.errorCode, isSelected)
        } else {
          ErrorImageHelper.getErrorImageDrawable(displayValue.errorCode, isSelected)
        }
      if (errorImageResId != null) {
        Image(
          painter = painterResource(id = errorImageResId),
          contentDescription = "Error ${displayValue.errorCode}",
          modifier = modifier
            .size(100.dp)
            .clickable(
              enabled = isSelectable,
              indication = null,
              interactionSource =
                remember {
                  MutableInteractionSource()
                },
            ) {
              onItemSelected?.invoke(item.emitValue)
            },
        )
      }
    }
  }
}

@PreviewTheme
@Composable
private fun SelectButtonGridPreview() {
  MeAppTheme {
    val errorButtons = SelectButtonHelper.createWifiModeButtons()
    val textItems = errorButtons

    SelectButtonGrid(
      items = textItems,
      isSelectable = true,
      onItemSelected = { value ->
        textItems.find { it.emitValue == value }?.isSelected = true
        // Handle selection
      },
    )
  }
}
