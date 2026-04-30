package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography

/**
 * A base list item component that provides a consistent layout for list items with title, subtitle,
 * leading content, trailing content, checkbox, and action button support.
 *
 * @param title The main title text to display
 * @param modifier Modifier for styling and layout
 * @param subTitle Optional subtitle text to display below the title
 * @param leadingContent Optional composable content to display at the start of the item
 * @param trailingContent Optional composable content to display at the end of the item
 * @param enableCheckbox Whether to show a checkbox for selection
 * @param isChecked Whether the checkbox is checked (only relevant if enableCheckbox is true)
 * @param enabled Whether the item is interactive
 * @param trailingAction Optional action button to display
 * @param shape The shape to apply to the item background
 * @param onClick Optional click handler for the entire item
 */
@Composable
fun BaseListItem(
  title: String,
  modifier: Modifier = Modifier,
  subTitle: String? = null,
  leadingContent: @Composable (() -> Unit)? = null,
  trailingContent: @Composable (() -> Unit)? = null,
  enableCheckbox: Boolean = false,
  checkboxDescription: String? = null,
  isChecked: Boolean = false,
  enabled: Boolean = true,
  trailingAction: ActionButton? = null,
  shape: Shape = RectangleShape,
  onClick: (() -> Unit)? = null,
) {
  remember { MutableInteractionSource() }
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(colorScheme.primaryBackground, shape)
        .clip(shape)
        .debounceClick(
          onClick = { onClick?.invoke() },
        )
        .padding(
          start = spacing.sm,
          top = spacing.sm,
          bottom = spacing.sm,
          end = if (trailingAction == null) spacing.sm else 0.dp,
        ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leadingContent?.invoke()

    Spacer(modifier = Modifier.width(spacing.md))

    // Content section with title and subtitle
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style =
          typography.body2.copy(
            color = colorScheme.textBody,
          ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subTitle != null) {
        Spacer(modifier = Modifier.height(spacing.x3s))
        Text(
          text = subTitle,
          style =
            typography.subHeading2.copy(
              color = colorScheme.textSubheading,
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Checkbox section
    if (enableCheckbox) {
      AppIcon(
        id = if (isChecked) AppIcons.Selection.CircleSelected else AppIcons.Selection.CircleUnselected,
        contentDescription = checkboxDescription ?: "",
        type = AppIconType.Primary,
        onClick = onClick,
        modifier = Modifier.size(24.dp),
      )
    }

    // Trailing action button
    if (trailingAction != null) {
      AppButton(
        label = trailingAction.text,
        onClick = trailingAction.action,
        type = ButtonType.TextPrimary,
        size = ButtonSize.Small,
        textTransform = TextTransform.NONE,
      )
    }

    // Custom trailing content
    trailingContent?.invoke()
  }
}

// region: Preview

@PreviewTheme
@Composable
fun BaseListItemAllCombinationsPreview() {
  MeAppTheme {
    Column {
      // Simple title only
      BaseListItem(
        title = "Title Only",
        modifier = Modifier,
      )
      // Title + Subtitle
      BaseListItem(
        title = "Title with Subtitle",
        subTitle = "Subtitle text",
        modifier = Modifier,
      )
      // Leading content
      BaseListItem(
        title = "With Leading",
        leadingContent = {
          AppIcon(
            id = AppIcons.Default.Close,
            contentDescription = "User Icon",
            modifier = Modifier.size(24.dp),
            type = AppIconType.Primary,
          )
        },
        modifier = Modifier,
      )
      // Trailing content
      BaseListItem(
        title = "With Trailing",
        trailingContent = {
          AppIcon(
            id = AppIcons.Default.RightCaret,
            contentDescription = "Right Arrow",
            modifier = Modifier.size(24.dp),
            type = AppIconType.Inverse,
          )
        },
        modifier = Modifier,
      )
      // Checkbox (unchecked)
      BaseListItem(
        title = "With Checkbox",
        enableCheckbox = true,
        isChecked = false,
        checkboxDescription = "Select item",
        modifier = Modifier,
      )
      // Checkbox (checked)
      BaseListItem(
        title = "With Checkbox Selected",
        enableCheckbox = true,
        isChecked = true,
        checkboxDescription = "Select item",
        modifier = Modifier,
      )
      // Trailing action button
      BaseListItem(
        title = "With Action Button",
        trailingAction =
          ActionButton(
            text = "Action",
            action = {},
          ),
        modifier = Modifier,
      )
      // Disabled state
      BaseListItem(
        title = "Disabled Item",
        enabled = false,
        modifier = Modifier,
      )
      // All features combined
      BaseListItem(
        title = "All Features",
        subTitle = "Subtitle",
        leadingContent = {
          AppIcon(
            id = AppIcons.Default.RightCaret,
            contentDescription = "User Icon",
            modifier = Modifier.size(24.dp),
            type = AppIconType.Primary,
          )
        },
        trailingContent = {
          AppIcon(
            id = AppIcons.Default.RightCaret,
            contentDescription = "Right Arrow",
            modifier = Modifier.size(24.dp),
            type = AppIconType.Inverse,
          )
        },
        enableCheckbox = true,
        isChecked = true,
        checkboxDescription = "Select item",
        trailingAction =
          ActionButton(
            text = "Action",
            action = {},
          ),
        enabled = true,
        modifier = Modifier,
      )
    }
  }
}
