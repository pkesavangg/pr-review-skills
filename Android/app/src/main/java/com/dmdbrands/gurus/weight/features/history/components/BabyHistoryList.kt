package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekGroup
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekHistory
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Baby history list grouped by week.
 * Iterates [BabyWeekGroup] — renders a header per group, then its entries. A birthday balloon is
 * shown on the day-row that falls on the baby's [birthdate] and on its enclosing week header.
 */
@Composable
fun BabyHistoryList(
    groups: List<BabyWeekGroup>,
    onItemClick: (BabyWeekHistory) -> Unit,
    babyWeightUnit: WeightUnit = WeightUnit.LB_OZ,
    birthdate: String? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        groups.forEach { group ->
            val groupHasBirthday = group.entries.any { DateTimeConverter.isBirthDate(it.dateKey, birthdate) }
            item(key = "header_${group.weekLabel}") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        horizontal = MeTheme.spacing.sm,
                        vertical = MeTheme.spacing.xs,
                    ),
                ) {
                    if (groupHasBirthday) {
                        AppIcon(
                            id = AppIcons.Default.BirthdayBalloon,
                            contentDescription = HistoryItemStrings.BirthdayBalloonContentDescription,
                            // Self-coloured vector (purple + white) — render untinted.
                            type = AppIconType.Default,
                            onClick = null,
                            modifier = Modifier.padding(end = MeTheme.spacing.x2s),
                        )
                    }
                    Text(
                        text = group.weekLabel,
                        style = MeTheme.typography.heading4,
                        color = MeTheme.colorScheme.textBody,
                    )
                }
            }
            items(
                items = group.entries,
                key = { "${group.weekLabel}_${it.date}" },
            ) { entry ->
                BabyHistoryItem(
                    item = entry,
                    onClick = { onItemClick(entry) },
                    babyWeightUnit = babyWeightUnit,
                    showBalloon = DateTimeConverter.isBirthDate(entry.dateKey, birthdate),
                )
            }
        }
    }
}
