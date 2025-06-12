package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.dashboard.helper.BOTTOM_NAV_ITEMS
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun MainBottomNav() {
    val topBackStack = LocalNavBackStack.current
    var selectedItem by remember { mutableStateOf(BOTTOM_NAV_ITEMS[0]) }

    LaunchedEffect(topBackStack.topLevelKey) {
        selectedItem = BOTTOM_NAV_ITEMS.find { it.route == topBackStack.topLevelKey } ?: BOTTOM_NAV_ITEMS[0]
    }

    NavigationBar(
        containerColor = MeAppTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            BOTTOM_NAV_ITEMS.forEach { item ->
                val isSelected = (selectedItem == item)
                val icon = if (isSelected && item.selectedIcon != null) item.selectedIcon else item.icon

                NavigationBarItem(
                    icon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painter = painterResource(id = icon),
                                contentDescription = null,
                                colorFilter = if (!isSelected || item.route == AppRoute.Main.AppSync) ColorFilter.tint(
                                    MeAppTheme.colorScheme.subheading,
                                ) else null,
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            color = MeAppTheme.colorScheme.subheading,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.W400,
                            textAlign = TextAlign.Center,
                        )

                    },
                    selected = false,
                    onClick = {
                        selectedItem = item
                        topBackStack.addTopLevel(item.route)
                    },
                )
            }

        }
    }
}

@PreviewTheme
@Composable
fun MainBottomNavPreview() {
    MeAppTheme {
        MainBottomNav()
    }
}
