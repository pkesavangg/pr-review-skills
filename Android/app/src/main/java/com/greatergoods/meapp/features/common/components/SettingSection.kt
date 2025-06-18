package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.model.SettingColorType
import com.greatergoods.meapp.features.common.model.SettingsItem
import com.greatergoods.meapp.features.common.model.SettingsItemType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * A section in the settings screen that displays a group of related settings items.
 *
 * @param title Optional title for the section
 * @param items List of settings items to display in this section
 */
@Composable
fun SettingsSection(
    title: String? = null,
    items: List<SettingsItem>,
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

        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
    }
}

/**
 * A row in the settings section that displays a single settings item.
 * Handles different types of settings items:
 * - Action: Shows a right caret icon
 * - Dropdown: Shows a down caret icon
 * - TextOnly: Shows only text without any icon
 * - CustomIcon: Shows a custom icon
 * - Custom: Shows custom composable content
 * - None: Shows no additional content
 *
 * @param item The settings item to display
 */
@Composable
private fun SettingsItemRow(item: SettingsItem) {
    val color =
        when (item.color) {
            SettingColorType.Default -> MeTheme.colorScheme.textBody
            SettingColorType.Primary -> MeTheme.colorScheme.wgPrimary
            SettingColorType.Tertiary -> MeTheme.colorScheme.textSubheading
            SettingColorType.Danger -> MeTheme.colorScheme.danger
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MeTheme.colorScheme.primaryBackground,
        onClick = item.onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MeTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = item.title,
                textType = TextType.Subtitle,
                color = color,
            )

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
                        else -> null
                    }
                if (value != null) {
                    Text(
                        text = value,
                        style = MeTheme.typography.body2,
                        color = MeTheme.colorScheme.textSubheading,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
                when (item.type) {
                    is SettingsItemType.Action -> {
                        AppIcon(
                            id = AppIcons.Default.RightCaret,
                            contentDescription = "Action",
                        )
                    }

                    is SettingsItemType.Dropdown -> {
                        AppIcon(
                            id = AppIcons.Filled.CaretDown,
                            contentDescription = "Dropdown",
                        )
                    }

                    is SettingsItemType.CustomIcon -> {
                        item.type.icon()
                    }

                    is SettingsItemType.Custom -> {
                        item.type.content()
                    }

                    is SettingsItemType.None, is SettingsItemType.TextOnly -> {
                        // No icon
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
            // Action Items Section
            SettingsSection(
                title = "Action Items",
                items =
                    listOf(
                        SettingsItem(
                            title = "Regular Action",
                            type = SettingsItemType.Action(),
                            onClick = {},
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

            // Dropdown Items Section
            SettingsSection(
                title = "Dropdown Items",
                items =
                    listOf(
                        SettingsItem(
                            title = "Gender",
                            type = SettingsItemType.Dropdown("Female"),
                            onClick = {},
                        ),
                        SettingsItem(
                            title = "Units",
                            type = SettingsItemType.Dropdown("lbs & feet"),
                            onClick = {},
                        ),
                    ),
            )

            // Text Only Items Section
            SettingsSection(
                title = "Text Only Items",
                items =
                    listOf(
                        SettingsItem(
                            title = "Height",
                            type = SettingsItemType.TextOnly("5' 7\""),
                            onClick = {},
                        ),
                        SettingsItem(
                            title = "Weight",
                            type = SettingsItemType.TextOnly("150 lbs"),
                            onClick = {},
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
                                    icon = { AppIcon(id = AppIcons.Default.Plus, contentDescription = "Plus") },
                                ),
                            onClick = {},
                        ),
                        SettingsItem(
                            title = "No Icon",
                            type = SettingsItemType.None,
                            onClick = {},
                        ),
                        SettingsItem(
                            title = "Custom Content",
                            type =
                                SettingsItemType.Custom {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppIcon(id = AppIcons.Default.Plus, contentDescription = "Plus")
                                        Text(
                                            text = "Custom Content",
                                            style = MeTheme.typography.body2,
                                            color = MeTheme.colorScheme.textSubheading,
                                        )
                                    }
                                },
                            onClick = {},
                        ),
                    ),
            )
        }
    }
}
