package com.greatergoods.meapp.features.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack

/**
 * Displays the Feeds screen with navigation buttons to other main screens.
 * Navigation is handled via LocalNavBackStack.
 */
@Composable
fun FeedsScreen() {
    val navBackStack = LocalNavBackStack.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Feeds Screen", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Here is some content from Feeds...")
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { navBackStack.add(AppRoute.Main.MyScales) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go to My Scales")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { navBackStack.add(AppRoute.Main.DeviceDetail.Overview) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go to Device Overview")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { navBackStack.add(AppRoute.Main.DeviceDetail.Settings) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go to Device Settings")
        }
    }
}

