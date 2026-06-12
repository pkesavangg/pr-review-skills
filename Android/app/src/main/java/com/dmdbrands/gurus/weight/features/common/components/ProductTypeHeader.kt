package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.typography

/**
 * Displays the currently selected product type with a dropdown icon.
 * Used in the app bar of Dashboard, History, and Manual Entry screens.
 */
@Composable
fun ProductTypeHeader(
    selectedProduct: ProductSelection?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = selectedProduct.displayName(),
            style = typography.heading5,
            color = selectedProduct.displayColor(),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            painter = painterResource(AppIcons.Default.ChevronDown),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colorScheme.textHeading,
        )
    }
}

@Composable
private fun ProductSelection?.displayColor(): Color = when (this) {
    is ProductSelection.MyWeight -> colorScheme.iconPrimary
    is ProductSelection.BloodPressure -> colorScheme.success
    is ProductSelection.Baby -> BabyPurple
    is ProductSelection.BabyScale -> BabyPurple
    null -> colorScheme.textHeading
}

private fun ProductSelection?.displayName(): String = when (this) {
    is ProductSelection.MyWeight -> "My Weight"
    is ProductSelection.BloodPressure -> "My BP"
    is ProductSelection.Baby -> profile.name
    is ProductSelection.BabyScale -> "Baby Scale"
    null -> "My Weight"
}

private val BabyPurple = Color(0xFF8841A4)

@PreviewTheme
@Composable
fun ProductTypeHeaderPreview() {
    MeAppTheme {
        ProductTypeHeader(
            selectedProduct = ProductSelection.MyWeight,
            onClick = {},
        )
    }
}
