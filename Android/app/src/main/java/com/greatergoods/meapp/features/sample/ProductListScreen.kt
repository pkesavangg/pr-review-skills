package com.greatergoods.meapp.features.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack

/**
 * Displays the Product List screen with buttons to open product details.
 * Navigation is handled via LocalNavBackStack.
 */
@Composable
fun ProductListScreen() {
    val navBackStack = LocalNavBackStack.current
    Column {
        Text("Product List")
        Spacer(Modifier.height(16.dp))
        listOf("p1", "p2", "p3").forEach { id ->
            Button(
                onClick = { navBackStack.add(AppRoute.Product.ProductDetail(id)) },
                modifier = Modifier.padding(4.dp)
            ) {
                Text("Open Product $id")
            }
        }
    }
}

