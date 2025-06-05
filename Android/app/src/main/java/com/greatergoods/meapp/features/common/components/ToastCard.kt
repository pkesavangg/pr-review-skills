package com.greatergoods.meapp.features.common.components

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.theme.MeAppTheme
import android.content.res.Configuration

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
    clearToast: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MeAppTheme.colorScheme.toastBackground,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            toast.title?.let {
                Text(
                    text = it,
                    style = MeAppTheme.typography.heading5,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = toast.message,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    style = MeAppTheme.typography.body2,
                )
            }
            toast.action?.let {
                Text(
                    text = it.text.uppercase(),
                    style = MeAppTheme.typography.button2,
                    fontWeight = FontWeight.Bold,
                    color = MeAppTheme.colorScheme.primaryAction,
                    modifier = Modifier
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

@Preview(name = "ToastCard Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "ToastCard Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewToastCard() {
    MeAppTheme {
        ToastCard(
            toast = Toast(title = "Success", message = "Operation completed"),
        )
    }
}
