package com.greatergoods.meapp.features.sample

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Displays the Product Detail screen for a given product ID.
 *
 * @param productId The ID of the product to display.
 */
@Composable
fun ProductDetailScreen(productId: String) {
    Text("Product Detail for $productId")
}

@PreviewTheme
@Composable
private fun PreviewProductDetailScreen() {
    MeAppTheme {
        ProductDetailScreen(productId = "p1")
    }
}
