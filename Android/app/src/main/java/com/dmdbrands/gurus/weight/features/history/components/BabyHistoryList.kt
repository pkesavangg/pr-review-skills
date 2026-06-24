package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekGroup
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekHistory
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Baby history list grouped by week.
 * Iterates [BabyWeekGroup] — renders a header per group, then its entries.
 */
@Composable
fun BabyHistoryList(
    groups: List<BabyWeekGroup>,
    onItemClick: (BabyWeekHistory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        groups.forEach { group ->
            item(key = "header_${group.weekLabel}") {
                Text(
                    text = group.weekLabel,
                    style = MeTheme.typography.heading4,
                    color = MeTheme.colorScheme.textBody,
                    modifier = Modifier.padding(
                        horizontal = MeTheme.spacing.sm,
                        vertical = MeTheme.spacing.xs,
                    ),
                )
            }
            items(
                items = group.entries,
                key = { "${group.weekLabel}_${it.date}" },
            ) { entry ->
                BabyHistoryItem(item = entry, onClick = { onItemClick(entry) })
            }
        }
    }
}
