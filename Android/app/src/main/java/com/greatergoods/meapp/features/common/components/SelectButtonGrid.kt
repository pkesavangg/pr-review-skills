package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.model.SelectButtonDisplayValue
import com.greatergoods.meapp.features.common.model.SelectButtonItem
import com.greatergoods.meapp.features.common.helper.ErrorImageHelper
import com.greatergoods.meapp.features.common.helper.SelectButtonHelper
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import com.greatergoods.meapp.theme.MeTheme.typography

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
  modifier: Modifier = Modifier
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
  modifier: Modifier = Modifier
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
      Icon(
        painter = painterResource(id = displayValue.imageResId),
        contentDescription = null,
        tint = contentColor,
        modifier = modifier
          .size(80.dp)
          .background(backgroundColor, shape = CircleShape)
          .clickable(
            enabled = isSelectable,
            indication = null,
            interactionSource =
              remember {
                MutableInteractionSource()
              }) {
            onItemSelected?.invoke(item.emitValue)
          },
      )
    }

    is SelectButtonDisplayValue.ErrorCode -> {
      val errorImageResId = ErrorImageHelper.getErrorImageDrawable(displayValue.errorCode)
      if (errorImageResId != null) {
        Icon(
          painter = painterResource(id = errorImageResId),
          contentDescription = "Error ${displayValue.errorCode}",
          tint = contentColor,
          modifier = modifier
            .size(100.dp)
            .background(backgroundColor, shape = CircleShape)
            .clickable(
              enabled = isSelectable,
              indication = null,
              interactionSource =
                remember {
                  MutableInteractionSource()
                }) {
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
        // Handle selection
      },
    )
  }
}
