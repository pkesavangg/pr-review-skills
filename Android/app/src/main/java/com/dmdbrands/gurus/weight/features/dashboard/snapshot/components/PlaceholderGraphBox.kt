package com.dmdbrands.gurus.weight.features.dashboard.snapshot.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun PlaceholderGraphBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                MeTheme.colorScheme.secondaryBackground,
                RoundedCornerShape(MeTheme.borderRadius.sm),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textSubheading,
        )
    }
}
