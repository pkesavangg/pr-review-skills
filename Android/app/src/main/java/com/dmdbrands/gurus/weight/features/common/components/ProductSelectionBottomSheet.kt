package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography
import kotlinx.coroutines.launch

private val BabyPurple = Color(0xFF8841A4)

/**
 * Bottom sheet showing available product selections.
 * Each item is a [ProductSelection] — MyWeight, BloodPressure, or Baby(profile).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSelectionBottomSheet(
    title: String,
    availableProducts: List<ProductSelection>,
    selectedProduct: ProductSelection?,
    onSelect: (ProductSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        sheetState = sheetState,
        modifier = Modifier
          .systemBarsPadding()
          .navigationBarsPadding(),
        onDismissRequest = onDismiss,
        containerColor = colorScheme.primaryBackground,
        scrimColor = colorScheme.overlay,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar with close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(AppIcons.Default.Close) {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = title,
                    style = typography.heading5,
                    color = colorScheme.textHeading,
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Product rows — each availableProduct is one row
            availableProducts.forEach { product ->
                ProductRow(
                    label = product.displayName(),
                    color = product.displayColor(),
                    isSelected = product == selectedProduct,
                    onClick = {
                        onSelect(product)
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun ProductRow(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typography.body2,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(
                if (isSelected) AppIcons.Selection.CircleSelected
                else AppIcons.Selection.CircleUnselected
            ),
            contentDescription = if (isSelected) "Selected" else "Not selected",
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) colorScheme.iconPrimary else colorScheme.utility,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = spacing.md),
        color = colorScheme.utility,
        thickness = 0.5.dp,
    )
}

@Composable
private fun ProductSelection.displayName(): String = when (this) {
    is ProductSelection.MyWeight -> "My Weight"
    is ProductSelection.BloodPressure -> "My Blood Pressure"
    is ProductSelection.Baby -> profile.name
}

@Composable
private fun ProductSelection.displayColor(): Color = when (this) {
    is ProductSelection.MyWeight -> colorScheme.iconPrimary
    is ProductSelection.BloodPressure -> colorScheme.success
    is ProductSelection.Baby -> BabyPurple
}

@PreviewTheme
@Composable
fun ProductSelectionBottomSheetPreview() {
    MeAppTheme {
        Column {
            ProductRow(
                label = "My Weight",
                color = Color(0xFF1565C0),
                isSelected = true,
                onClick = {},
            )
            ProductRow(
                label = "My Blood Pressure",
                color = Color(0xFF458239),
                isSelected = false,
                onClick = {},
            )
            ProductRow(
                label = "Tammy Thompson",
                color = BabyPurple,
                isSelected = false,
                onClick = {},
            )
        }
    }
}
