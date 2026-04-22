package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

@Composable
fun ReadingArrivalCard(
    modifier: Modifier = Modifier,
    readingToast: ReadingToast,
    clearToast: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .cssBoxShadow(
                color = colorScheme.glow,
                offsetX = 2.dp,
                offsetY = 2.dp,
                blurRadius = 8.dp,
                spread = 0.dp,
                cornerRadius = 10.dp,
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.toastBackground,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (readingToast.assignedTo != null) {
                AssignedContent(readingToast, clearToast)
            } else {
                UnassignedContent(readingToast, clearToast)
            }
        }
    }
}

@Composable
private fun UnassignedContent(
    readingToast: ReadingToast,
    clearToast: () -> Unit,
) {
    Text(
        text = ReadingToastStrings.title(readingToast.type),
        style = MeTheme.typography.heading5,
        color = colorScheme.textBody,
    )
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = readingToast.value,
            style = MeTheme.typography.heading4,
            color = colorScheme.success,
        )
        Text(
            text = " · ${readingToast.timestamp}",
            style = MeTheme.typography.body2,
            color = colorScheme.textSubheading,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ReadingToastStrings.secondaryAction(readingToast.type),
            style = MeTheme.typography.button2,
            fontWeight = FontWeight.Bold,
            color = colorScheme.textBody,
            modifier = Modifier
                .clickable {
                    readingToast.onDismiss()
                    clearToast()
                }
                .padding(vertical = 6.dp),
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.textBody,
            ),
            onClick = {
                readingToast.onAssign()
                clearToast()
            },
        ) {
            Text(
                text = ReadingToastStrings.primaryAction(readingToast.type),
                style = MeTheme.typography.button2,
                fontWeight = FontWeight.Bold,
                color = colorScheme.toastBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AssignedContent(
    readingToast: ReadingToast,
    clearToast: () -> Unit,
) {
    Text(
        text = readingToast.value,
        style = MeTheme.typography.heading4,
        color = colorScheme.success,
    )
    Text(
        text = ReadingToastStrings.assignedTo(readingToast.assignedName ?: ""),
        style = MeTheme.typography.body2,
        color = colorScheme.textBody,
    )
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = ReadingToastStrings.WrongBaby,
            style = MeTheme.typography.body2,
            color = colorScheme.textSubheading,
        )
        Text(
            text = ReadingToastStrings.Reassign,
            style = MeTheme.typography.button2,
            fontWeight = FontWeight.Bold,
            color = colorScheme.textBody,
            modifier = Modifier.clickable {
                readingToast.onReassign()
            },
        )
    }
}

@PreviewTheme
@Composable
private fun ReadingArrivalCardUnassignedPreview() {
    MeAppTheme {
        ReadingArrivalCard(
            readingToast = ReadingToast(
                value = "14 lbs 6 oz",
                timestamp = "Just now",
                type = ProductType.BABY,
            ),
        )
    }
}

@PreviewTheme
@Composable
private fun ReadingArrivalCardAssignedPreview() {
    MeAppTheme {
        ReadingArrivalCard(
            readingToast = ReadingToast(
                value = "14 lbs 6 oz",
                timestamp = "Just now",
                type = ProductType.BABY,
                assignedTo = "baby-123",
                assignedName = "Emma",
            ),
        )
    }
}

@PreviewTheme
@Composable
private fun ReadingArrivalCardWeightPreview() {
    MeAppTheme {
        ReadingArrivalCard(
            readingToast = ReadingToast(
                value = "149.2 lbs",
                timestamp = "Just now",
                type = ProductType.MY_WEIGHT,
            ),
        )
    }
}
