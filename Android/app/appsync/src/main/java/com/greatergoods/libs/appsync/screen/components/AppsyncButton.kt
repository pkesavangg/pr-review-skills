package com.greatergoods.libs.appsync.screen.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.libs.appsync.R

@Composable
fun AppsyncButton(
    onClick: () -> Unit,
    src: Int,
    contentDescription: String,
) {
    FilledIconButton(
        onClick = onClick,
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        shape = RoundedCornerShape(4.dp),
        modifier =
            Modifier
                .width(32.dp)
                .height(32.dp)
                .semantics { contentDescription },
    ) {
        Icon(
            painter = painterResource(src),
            contentDescription = contentDescription,
            modifier = Modifier.width(12.dp),
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun AppsyncButtonPreview() {
    AppsyncButton(
        {
        },
        R.drawable.ic_close,
        contentDescription = "",
    )
}
