package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography
import kotlinx.coroutines.launch

class PickerState<T>(
    initialValue: T,
) {
    private var _selectedItem by mutableStateOf(initialValue)

    fun setItem(item: T) {
        _selectedItem = item
    }

    val item: T get() = _selectedItem
}

@Composable
fun <T> rememberPickerState(initialValue: T) = remember { PickerState(initialValue) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> AppPicker(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 3,
    itemHeight: Dp = 40.dp,
    itemWidth: Dp = 100.dp,
    labelMapper: (T, Boolean) -> String = { it, _ -> it.toString() },
    customItem: (@Composable (T, Boolean) -> Unit)? = null,
) {
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = items.indexOf(selectedItem).coerceAtLeast(0),
        )
    val coroutineScope = rememberCoroutineScope()
    val snapFling = rememberSnapFlingBehavior(listState)

    val dividerColor = colorScheme.textBody

    // Convert dp to px **in the composable function body**
    val pxPerItem = with(LocalDensity.current) { itemHeight.toPx() }

    // Find the item currently centered in the view
    val currentCenteredIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val threshold = pxPerItem / 2
            val index = listState.firstVisibleItemIndex + if (offset > threshold) 1 else 0
            index.coerceIn(0, items.lastIndex)
        }
    }

    // Snap to selected when it changes externally
    LaunchedEffect(selectedItem) {
        val idx = items.indexOf(selectedItem)
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    // When scroll stops, snap to closest and notify parent
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val item = items.getOrNull(currentCenteredIndex) ?: return@LaunchedEffect
            if (item != selectedItem) onItemSelected(item)
        }
    }

    // Picker UI
    Box(
        modifier =
            modifier
                .height(itemHeight * visibleItemsCount)
                .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapFling,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items.size) { index ->
                val item = items[index]
                val isSelected = index == currentCenteredIndex
                Box(
                    Modifier
                        .height(itemHeight)
                        .width(itemWidth)
                        .background(Color.Transparent)
                        .clickable {
                            coroutineScope.launch { listState.animateScrollToItem(index) }
                            onItemSelected(item)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (customItem != null) {
                        customItem(item, isSelected)
                    } else {
                        Text(
                            text = labelMapper(item, isSelected),
                            style =
                                if (isSelected) {
                                    typography.body2.copy(
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    typography.body2
                                },
                            color =
                                if (isSelected) {
                                    colorScheme.textBody
                                } else {
                                    colorScheme.textSubheading
                                },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        // Highlight center with only top and bottom lines
        HorizontalDivider(
            Modifier
                .width(itemWidth)
                .align(Alignment.Center)
                .offset(y = -itemHeight / 2),
            color = dividerColor,
            thickness = 2.dp,
        )
        HorizontalDivider(
            Modifier
                .width(itemWidth)
                .align(Alignment.Center)
                .offset(y = itemHeight / 2),
            color = dividerColor,
            thickness = 2.dp,
        )
    }
}

@Composable
private fun Float.dpToPx(): Float = this * LocalDensity.current.density

@PreviewTheme
@Composable
fun AppPickerPreview() {
    MeAppTheme {
        val items = listOf(159, 160, 161, 13, 14, 41, 1414, 4141)
        val state = rememberPickerState(160)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppPicker(
                items = items,
                selectedItem = state.item,
                onItemSelected = { it -> state.setItem(it) },
                labelMapper = { it, selected -> "$it cm" },
                itemHeight = (spacing.sm * 2) + 24.dp,
            )
        }
    }
}
