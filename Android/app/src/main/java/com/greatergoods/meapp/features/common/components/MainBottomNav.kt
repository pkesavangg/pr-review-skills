package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.dashboard.enum.BOTTOM_NAV_ITEMS
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Stateless bottom navigation bar.
 *
 * @param selectedIndex The index of the currently selected item.
 * @param onItemSelected Callback when an item is selected (index, item).
 */
@Composable
fun MainBottomNav(badgeVisible: List<AppRoute> = emptyList()) {
    var selectedItem by remember { mutableStateOf(BOTTOM_NAV_ITEMS[0]) }
    val topBackStack = LocalNavBackStack.current
    val backStack = topBackStack.getStackForTopLevel(AppRoute.Home)

    LaunchedEffect(backStack.lastOrNull()) {
        selectedItem =
            BOTTOM_NAV_ITEMS.find { it.route == backStack.lastOrNull() } ?: BOTTOM_NAV_ITEMS[0]
    }

    NavigationBar(
        modifier = Modifier.topBorder(0.6.dp, MeTheme.colorScheme.utility),
        containerColor = MeTheme.colorScheme.primaryBackground,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            BOTTOM_NAV_ITEMS.forEachIndexed { index, item ->
                val isSelected = (selectedItem == item)
                val icon =
                    if (isSelected && item.selectedIcon != null) item.selectedIcon else item.icon

                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                if (item.route in badgeVisible) {
                                    // Dot badge
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .align(Alignment.BottomEnd)
                                                .background(Color.Red),
                                    )
                                }
                            },
                        ) {
                            Image(
                                painter = painterResource(id = icon),
                                contentDescription = null,
                                colorFilter =
                                    if (!isSelected || item.route == AppRoute.Main.AppSync) {
                                        ColorFilter.tint(
                                            MeTheme.colorScheme.textSubheading,
                                        )
                                    } else {
                                        null
                                    },
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            color = MeTheme.colorScheme.textSubheading,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.W400,
                            textAlign = TextAlign.Center,
                        )
                    },
                    selected = false,
                    onClick = {
                        selectedItem = item
                        topBackStack.addRoute(item.route, AppRoute.Home, popUpTo = AppRoute.Main.Dashboard)
                    },
                )
            }
        }
    }
}

fun Modifier.topBorder(
    strokeWidth: Dp,
    color: Color
): Modifier = this.then(
    Modifier.drawBehind {
        val px = strokeWidth.toPx()
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = px,
        )
    },
)

@PreviewTheme
@Composable
fun MainBottomNavDemoScreenPreview() {
    MeAppTheme {
        MainBottomNav(badgeVisible = listOf(AppRoute.Main.Dashboard))
    }
}
