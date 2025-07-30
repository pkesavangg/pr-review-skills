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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

/**
 * Stateless UI for displaying a toast card.
 * @param modifier Modifier for layout and drag offset.
 * @param toast Toast data model.
 * @param clearToast Callback to clear the toast when action is performed.
 */
@Composable
fun ToastCard(
    modifier: Modifier = Modifier,
    toast: Toast,
    clearToast: () -> Unit = {},
) {
    Card(
        modifier =
            modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(10.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = colorScheme.toastBackground,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            toast.title?.let {
                Text(
                    text = it,
                    style = MeTheme.typography.heading5,
                    color = colorScheme.textBody,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = toast.message,
                    style = MeTheme.typography.body2,
                    color = colorScheme.textBody,
                )
            }
            toast.action?.let {
                Text(
                    text = it.text.uppercase(),
                    style = MeTheme.typography.button2,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primaryAction,
                    modifier =
                        Modifier
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                            .clickable {
                                it.action()
                                clearToast()
                            },
                )
            }
        }
    }
}
