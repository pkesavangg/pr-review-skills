package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.enum.GraphSegment
import com.greatergoods.meapp.theme.MeTheme

/**
 * Segmented control for graph period selection (TOTAL, YEAR, MONTH, WEEK).
 * Matches Figma UI.
 */
@Composable
fun GraphSegmentControl(
    selected: GraphSegment,
    onSelect: (GraphSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
    ) {
        GraphSegment.entries.forEach { segment ->
            val isSelected = segment == selected
            Button(
                onClick = { onSelect(segment) },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFF2C2827) else Color(0xFF4079CC),
                        contentColor = Color.White,
                    ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp),
                elevation = null,
            ) {
                Text(
                    text = segment.name,
                    style =
                        MeTheme.typography.button1.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                )
            }
        }
    }
}
