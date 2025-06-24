package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * A circular profile avatar that displays the first letter of the provided text and supports active/inactive states.
 *
 * @param text The text from which to extract the first letter.
 * @param modifier Modifier for styling.
 * @param size Size of the circular profile image in dp.
 * @param isActive Whether the user is active, which determines the avatar's style.
 * @param enabled Disable the avatar.
 */
@Composable
fun AppProfileAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isActive: Boolean = true,
    enabled: Boolean = true,
) {
    val backgroundColor = when {
        isActive -> MeTheme.colorScheme.iconPrimary
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> MeTheme.colorScheme.primaryActionDisabled
        isActive -> MeTheme.colorScheme.inverseAction
        else -> MeTheme.colorScheme.primaryAction
    }
    val borderModifier = when {
        !isActive && enabled -> Modifier.border(2.dp, MeTheme.colorScheme.iconPrimary, CircleShape)
        !isActive && !enabled -> Modifier.border(2.dp, MeTheme.colorScheme.iconPrimaryDisabled, CircleShape)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(borderModifier)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.firstOrNull()?.uppercase() ?: "",
            style = MeTheme.typography.heading6,
            color = textColor,
        )
    }
}

@PreviewTheme
@Composable
fun AppProfileImagePreview() {
    MeAppTheme {
        AppScaffold("") {
            Column {
                AppProfileAvatar(text = "Kevin", isActive = true, enabled = true)
                Spacer(Modifier.height(8.dp))
                AppProfileAvatar(text = "Kevin", isActive = false, enabled = true)
                Spacer(Modifier.height(8.dp))
                AppProfileAvatar(text = "Kevin", isActive = false, enabled = false)
            }
        }
    }
}
