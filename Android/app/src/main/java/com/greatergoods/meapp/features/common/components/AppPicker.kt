package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.model.PickerState
import com.greatergoods.meapp.theme.MeAppTheme.typography
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty1
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme
import com.greatergoods.meapp.theme.MeAppTheme.spacing

@Composable
fun <T> rememberPickerState(initialValue: T) = remember { PickerState(initialValue) }


@Composable
fun <T> AppPicker(
    modifier: Modifier = Modifier,
    items: List<T>,
    state: PickerState<T>,
    visibleElements: Int = 3,
    labelMapper: (T) -> String,
    isFocusNeeded: Boolean = false,
    customItemView: @Composable ((T, Boolean) -> Unit)? = null,
    onItemChange: (T) -> Unit = {}
) {
    val visibleItemsMiddle = visibleElements / 2
    val listScrollCount = items.size
    val scope = rememberCoroutineScope()

    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(state.item))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val itemHeightDp = 40.dp
    val dividerColor = colorScheme.body.copy(alpha = 0.5f)
    val dividerThickness = 1.dp

    LaunchedEffect(items) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }
            .collect { hidden ->
                if (hidden > 40) {
                    state.setItem(
                        items[listState.firstVisibleItemIndex + 1]
                    )
                } else {
                    state.setItem(
                        items[listState.firstVisibleItemIndex]
                    )
                }
            }
    }
    Box(
        modifier = Modifier
            .height(itemHeightDp * (visibleElements - 1) + 40.dp)
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        // Divider above selected item
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = spacing.md)
                .offset(y = -itemHeightDp / 2f),
            color = dividerColor,
            thickness = dividerThickness
        )
        // Divider below selected item
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = spacing.md)
                .offset(y = itemHeightDp / 2f),
            color = dividerColor,
            thickness = dividerThickness
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceEvenly,
            contentPadding = PaddingValues(vertical = itemHeightDp * (visibleItemsMiddle)*1.2f),
            modifier = Modifier
                .fillMaxHeight()
        ) {
            items(listScrollCount) { index ->
                val currentItem = items[index]
                val isSelected = state.item == currentItem && isFocusNeeded
                val modifier = Modifier
                    .height(itemHeightDp + 4.dp)
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .clickable {
                        scope.launch {
                            listState.animateScrollToItem(index)
                        }
                    }
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)

                if (customItemView != null) {
                    Box(modifier = modifier) {
                        customItemView(currentItem, isSelected)
                    }
                } else {
                    val label = labelMapper(currentItem)
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        style = if (isSelected) typography.body2.copy(fontWeight = FontWeight.Bold) else typography.body2,
                        color = if (isSelected) colorScheme.heading else colorScheme.subheading,
                        modifier =if (isSelected)  Modifier.fillMaxWidth().padding(vertical = 3.dp) else Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppPickerPreview() {
    MeAppTheme {
        val items = listOf(159, 160, 161)
        val state = rememberPickerState(160)
        AppPicker(
            items = items,
            state = state,
            labelMapper = { "$it cm" },
            isFocusNeeded = true
        )
    }
}

/**
 * Default values for AppPicker height pickers.
 */
object AppPickerDefaults {
    /**
     * List of height values in centimeters (150 to 200 cm).
     */
    val cmHeights: List<Int> = (150..200).toList()

    /**
     * List of height values in feet/inches (4'0" to 7'0").
     * Each entry is a Pair of feet to inches (0-11).
     */
    val feetHeights: List<Pair<Int, Int>> = buildList {
        for (feet in 4..7) {
            for (inch in 0..11) {
                add(feet to inch)
            }
        }
    }
}
