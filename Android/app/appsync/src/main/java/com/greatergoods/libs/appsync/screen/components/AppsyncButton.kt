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

/**
 * Custom icon button component designed specifically for the AppSync scanning interface.
 *
 * This composable creates a standardized button with consistent styling that matches
 * the AppSync design system. The button features:
 * - Fixed 32dp x 32dp size for consistent touch targets
 * - White background with black icon for high contrast
 * - Rounded corners (4dp radius) for modern appearance
 * - Proper accessibility support with content descriptions
 * - Disabled state styling with reduced opacity
 * - 12dp icon size for optimal visibility
 *
 * The button is designed to be used in the overlay controls for zoom, manual entry,
 * and close actions. It provides a consistent user experience across the scanning
 * interface.
 *
 * @param onClick Callback function invoked when the button is pressed
 * @param src Resource ID of the icon to display in the button
 * @param contentDescription Accessibility description for screen readers
 * @param enabled Whether the button is interactive. When false, the button appears
 *                dimmed and does not respond to touch events
 */
@Composable
fun AppsyncButton(
    onClick: () -> Unit,
    src: Int,
    contentDescription: String,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors =
            IconButtonDefaults.iconButtonColors(
                // White background for enabled state
                containerColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                // Black icon for enabled state
                contentColor = if (enabled) Color.Black else Color.Black.copy(alpha = 0.5f),
                // Dimmed background for disabled state
                disabledContainerColor = Color.White.copy(alpha = 0.5f),
                // Dimmed icon for disabled state
                disabledContentColor = Color.Black.copy(alpha = 0.5f),
            ),
        // Rounded corners for modern appearance
        shape = RoundedCornerShape(4.dp),
        modifier =
            Modifier
              .width(32.dp) // Fixed width for consistent touch target
                .height(32.dp) // Fixed height for consistent touch target
                .semantics { contentDescription },
        // Accessibility support
    ) {
        Icon(
            painter = painterResource(src),
            contentDescription = contentDescription,
            modifier = Modifier.width(16.dp), // Fixed icon size for consistency
        )
    }
}

/**
 * Preview composable for the AppsyncButton in enabled state.
 *
 * This preview shows how the button appears when it's interactive and ready
 * for user input. Used for development and testing purposes.
 */
@Preview(showSystemUi = true)
@Composable
fun AppsyncButtonPreview() {
    AppsyncButton(
        onClick = { /* Preview only */ },
        src = R.drawable.ic_close,
        contentDescription = "Close button preview",
    )
}

/**
 * Preview composable for the AppsyncButton in disabled state.
 *
 * This preview shows how the button appears when it's not interactive,
 * with reduced opacity to indicate the disabled state. Used for development
 * and testing purposes.
 */
@Preview(showSystemUi = true)
@Composable
fun AppsyncButtonDisabledPreview() {
    AppsyncButton(
        onClick = { /* Preview only */ },
        src = R.drawable.ic_close,
        contentDescription = "Disabled close button preview",
        enabled = false,
    )
}
