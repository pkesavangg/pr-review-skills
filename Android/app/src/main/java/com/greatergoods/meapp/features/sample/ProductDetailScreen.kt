package com.greatergoods.meapp.features.sample

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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

@Preview(name = "ProductDetailScreen Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewProductDetailScreenLight() {
    MeAppTheme(darkTheme = false) {
        ProductDetailScreen(productId = "p1")
    }
}

@Preview(name = "ProductDetailScreen Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewProductDetailScreenDark() {
    MeAppTheme(darkTheme = true) {
        ProductDetailScreen(productId = "p1")
    }
} 