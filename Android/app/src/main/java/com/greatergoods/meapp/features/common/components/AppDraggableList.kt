package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.Account
import com.greatergoods.meapp.features.common.helper.AppDraggableListState
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun <T> AppDraggableList(
    items: List<T>,
    draggableListState: AppDraggableListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    iconWidth: Dp = 40.dp,
    onDelete: (T) -> Unit,
    isItemDraggable: (T) -> Boolean = { true },
    itemContent: @Composable (item: T, progress: Float) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        itemsIndexed(items, key = { _, item -> (item as? Account)?.id ?: item.hashCode() }) { index, item ->
            AppDraggableListItem(
                actionContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onDelete(item) }
                            .background(MeTheme.colorScheme.danger),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            id = AppIcons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(24.dp),
                            type = AppIconType.Secondary,
                        )
                    }
                },
                isDraggable = !lazyListState.isScrollInProgress && isItemDraggable(item),
                iconWidth = iconWidth,
                index = index,
                onActionOpened = { draggableListState.set(it) },
                showAction = draggableListState.openCardIndex == index,
            ) { progress ->
                itemContent(item, progress)
            }
        }
    }
}
