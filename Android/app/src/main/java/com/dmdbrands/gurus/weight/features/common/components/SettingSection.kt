package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.helper.DateFormatHelper
import com.dmdbrands.gurus.weight.features.common.model.SettingColorType
import com.dmdbrands.gurus.weight.features.common.model.SettingsItem
import com.dmdbrands.gurus.weight.features.common.model.SettingsItemType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * A section in the settings screen that displays a group of related settings items.
 *
 * @param title Optional title for the section
 * @param items List of settings items to display in this section
 */
@Composable
fun SettingsSection(
    title: String? = null,
    hasBottomSpace: Boolean = true,
    items: List<SettingsItem>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MeTheme.typography.heading4,
                color = MeTheme.colorScheme.textHeading,
                modifier = Modifier.padding(bottom = MeTheme.spacing.sm),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MeTheme.borderRadius.sm),
            colors =
                CardDefaults.cardColors(
                    containerColor = MeTheme.colorScheme.primaryBackground,
                ),
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    SettingsItemRow(item)

                    if (index < items.size - 1) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MeTheme.colorScheme.utility,
                        )
                    }
                }
            }
        }

        if (hasBottomSpace) {
            Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
        }
    }
}

/**
 * A row in the settings section that displays a single settings item.
 * Handles different types of settings items:
 * - Action: Shows a right caret icon (icon clickable)
 * - Dropdown: Shows a down/up caret icon (icon clickable, toggles state)
 * - TextOnly: Shows only text without any icon (whole row clickable)
 * - CustomIcon: Shows a custom icon (icon clickable)
 * - Custom: Shows custom composable content (whole row clickable)
 * - None: Shows no additional content (whole row clickable)
 *
 * @param item The settings item to display
 */
@Composable
private fun SettingsItemRow(
    item: SettingsItem,
) {
    val color =
        when (item.color) {
            SettingColorType.Default -> if (item.enabled) MeTheme.colorScheme.textBody else MeTheme.colorScheme.utility
            SettingColorType.Primary -> MeTheme.colorScheme.wgPrimary
            SettingColorType.Tertiary -> MeTheme.colorScheme.textSubheading
            SettingColorType.Danger -> MeTheme.colorScheme.danger
        }
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier)
        .debounceClick(onClick = { if (item.enabled) item.onClick.invoke() }),
      color = MeTheme.colorScheme.primaryBackground,
    ) {
        Row(
            modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(MeTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Unread indicator dot
                if (item.showUnreadIndicator) {
                    Box(
                      modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MeTheme.colorScheme.danger),
                    )
                }

                AppText(
                    text = item.title,
                    textType = TextType.Subtitle,
                    color = color,
                    enabled = item.enabled,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val value =
                    when (item.type) {
                        is SettingsItemType.Action -> item.type.text
                        is SettingsItemType.CustomIcon -> item.type.text
                        is SettingsItemType.Dropdown -> item.type.text
                        is SettingsItemType.TextOnly -> item.type.text
                        is SettingsItemType.TextDate ->
                            DateFormatHelper.formatDisplayDate(
                                item.type.rawDate,
                            )

                        else -> null
                    }
                if (value != null) {
                    val windowSize = LocalWindowInfo.current.containerSize
                    val isTablet = with(LocalDensity.current) {
                        windowSize.width.toDp() > 600.dp
                    }
                    val maxWidth = if (isTablet) 350.dp else 160.dp

                    Text(
                        text = value,
                        style = MeTheme.typography.body2,
                        color = if (item.enabled) MeTheme.colorScheme.textSubheading else MeTheme.colorScheme.utility,
                        textAlign = TextAlign.End,
                        maxLines = item.maxLines,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = maxWidth),
                    )
                }
                when (item.type) {
                    is SettingsItemType.Action -> {
                        AppIcon(
                            id = AppIcons.Default.RightCaret,
                            contentDescription = "Action",
                            enabled = item.enabled,
                            onClick = {item.onClick.invoke()},
                        )
                    }

                    is SettingsItemType.Dropdown -> {
                        AppIcon(
                            id = AppIcons.Filled.CaretDown,
                            contentDescription = "Dropdown",
                            tintColor = MeTheme.colorScheme.iconSecondary,
                            enabled = item.enabled,
                            onClick = {item.onClick.invoke()},
                        )
                    }

                    is SettingsItemType.CustomIcon -> {
                        item.type.icon()
                    }

                    is SettingsItemType.Custom -> {
                        item.type.content()
                    }

                    is SettingsItemType.Toggle -> {
                        AppToggle(
                            checked = item.type.checked,
                            onCheckedChange = item.type.onCheckedChange
                        )
                    }

                    is SettingsItemType.None, is SettingsItemType.TextOnly -> {
                        // No icon
                    }

                    is SettingsItemType.TextDate -> {
                    }
                }
            }
        }

    }
}

@PreviewTheme
@Composable
private fun SettingsSectionPreview() {
    MeAppTheme {
        Column {
            // Action Items Section (Icon clickable only)
            SettingsSection(
                title = "Action Items",
                items =
                    listOf(
                        SettingsItem(
                            title = "Regular Action",
                            type = SettingsItemType.Action(),
                            onClick = {}, // Row click (not triggered)
                        ),
                        SettingsItem(
                            title = "Primary Action",
                            type = SettingsItemType.Action(),
                            color = SettingColorType.Primary,
                            onClick = {},
                        ),
                        SettingsItem(
                            title = "Danger Action",
                            type = SettingsItemType.Action(),
                            color = SettingColorType.Danger,
                            onClick = {},
                        ),
                    ),
            )

            // Dropdown Items Section (Centralized state management)
            SettingsSection(
                title = "Dropdown Items",
                items =
                    listOf(
                        SettingsItem(
                            title = "Gender",
                            type = SettingsItemType.Dropdown("Female"),
                            onClick = {
                                // Set dialog and expand dropdown
                            },
                        ),
                        SettingsItem(
                            title = "Units",
                            type = SettingsItemType.Dropdown("lbs & feet"),
                            onClick = {
                                // Set dialog and expand dropdown
                            },
                        ),
                    ),
            )

            // Text Only Items Section (Whole row clickable)
            SettingsSection(
                title = "Text Only Items",
                items =
                    listOf(
                        SettingsItem(
                            title = "Height",
                            type = SettingsItemType.TextOnly("5' 7\""),
                            onClick = {}, // Whole row clickable
                        ),
                        SettingsItem(
                            title = "Weight",
                            type = SettingsItemType.TextOnly("150 lbs"),
                            onClick = {}, // Whole row clickable
                        ),
                    ),
            )

            // Custom Items Section
            SettingsSection(
                title = "Custom Items",
                items =
                    listOf(
                        SettingsItem(
                            title = "Custom Icon",
                            type =
                                SettingsItemType.CustomIcon(
                                  text = "Custom",
                                  icon = { AppIcon(id = AppIcons.Outlined.PlusCircle, contentDescription = "Plus") },
                                ),
                            onClick = {}, // Row click (not triggered)
                        ),
                        SettingsItem(
                            title = "No Icon",
                            type = SettingsItemType.None,
                            onClick = {}, // Whole row clickable
                        ),
                        SettingsItem(
                            title = "Custom Content",
                            type =
                                SettingsItemType.Custom {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppIcon(id = AppIcons.Outlined.PlusCircle, contentDescription = "Plus")
                                        Text(
                                            text = "Custom Content",
                                            style = MeTheme.typography.body2,
                                            color = MeTheme.colorScheme.textSubheading,
                                        )
                                    }
                                },
                            onClick = {}, // Whole row clickable
                        ),
                    ),
            )
        }
    }
}


