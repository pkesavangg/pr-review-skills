package com.greatergoods.meapp.features.common.components

import androidx.compose.material3.NavigationBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color.Companion.White
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.dashboard.enum.BOTTOM_NAV_ITEMS
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun MainBottomNav() {
    val topBackStack = LocalNavBackStack.current
    var selectedItem by remember { mutableStateOf(BOTTOM_NAV_ITEMS[0]) }

    NavigationBar(
        containerColor = White,
    ) {
    }
}

@PreviewTheme
@Composable
fun MainBottomNavPreview() {
    MeAppTheme {
        MainBottomNav()
    }
}
