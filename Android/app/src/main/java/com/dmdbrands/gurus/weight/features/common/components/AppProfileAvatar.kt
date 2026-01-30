package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * A circular profile avatar that displays the first letter of the provided text and supports active/inactive states.
 *
 * @param text The text from which to extract the first letter.
 * @param modifier Modifier for styling.
 * @param size Size of the circular profile image in dp.
 * @param isInfoIcon Whether to display an info icon overlay.
 * @param isActive Whether the user is active, which determines the avatar's style.
 * @param enabled Disable the avatar.
 * @param onLongPress Callback triggered when the avatar is long pressed.
 */
@Composable
fun AppProfileAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isInfoIcon: Boolean = false,
    isActive: Boolean = true,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
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

    // Create gesture modifier for long press
    val gestureModifier = if (enabled && onLongPress != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongPress() }
            )
        }
    } else {
        Modifier
    }

    if (!isInfoIcon) {
        val avatarText = text.trim().takeIf { it.isNotEmpty() }?.let { input ->
            var index = 0
            while (index < input.length) {
                val codePoint = input.codePointAt(index)
                val charStr = String(Character.toChars(codePoint))
                if (!charStr.isEmoji()) {
                    return@let charStr.uppercase()
                }
                index += Character.charCount(codePoint)
            }
            "" // return blank if no non-emoji char found
        } ?: ""
        // Default single avatar
        Box(
            modifier = modifier
              .size(size)
              .then(borderModifier)
              .then(gestureModifier)
              .clip(CircleShape)
              .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarText.uppercase(),
                style = MeTheme.typography.heading6,
                color = textColor,
            )
        }
    } else {
        // Combined "K" + profile icon
        Box(
            modifier = modifier
              .width(size * 1.6f) // adjusted width for better centering
                .height(size)
              .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            // Profile icon - overlapping on the right (rendered first, lower z-index)
            AppIcon(
                id = AppIcons.Default.profile,
                contentDescription = "Profile",
                type = AppIconType.Primary,
                modifier = Modifier
                  .align(Alignment.CenterEnd)
                  .size(size),
            )

            // Letter box - positioned on the left (rendered second, higher z-index)
            Box(
                modifier = Modifier
                  .align(Alignment.CenterStart)
                  .size(size / 1.06f)
                  .border(3.dp, MeTheme.colorScheme.inverseAction, CircleShape)
                  .clip(CircleShape)
                  .background(backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text.firstOrNull()?.uppercase() ?: "",
                    style = MeTheme.typography.heading4,
                    color = textColor,
                )
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppProfileImagePreview() {
    MeAppTheme {
        AppScaffold("") {
            Column {
                AppProfileAvatar(text = "Kevin", isActive = true, enabled = true, onLongPress = {})
                Spacer(Modifier.height(8.dp))
                AppProfileAvatar(text = "Kevin", isActive = false, enabled = true, onLongPress = {})
                Spacer(Modifier.height(8.dp))
                AppProfileAvatar(text = "Kevin", isActive = false, enabled = false)
            }
        }
    }
}

private fun String.isEmoji(): Boolean {
  if (this.isEmpty()) return false
  val codePoint = this.codePointAt(0)
  return when (codePoint) {
    in 0x1F600..0x1F64F, // Emoticons 😀 😁 😂 🤣 😃 😄 😅 😆 😉 😊 😋 😎 😍 😘 😗 😙 😚 ☺️
    in 0x1F300..0x1F5FF, // Misc symbols and pictographs 🌀 🌁 🌂 🌃 🌄 🌅 🌆 🌇 🌈 🌉
    in 0x1F680..0x1F6FF, // Transport and map symbols 🚀 🚁 🚂 🚃 🚄 🚅
    in 0x2600..0x26FF,   // Misc symbols ☀️ ☁️ ☂️ ☃️
    in 0x2700..0x27BF,   // Dingbats ✂️ ✈️ ✉️
    in 0x1F900..0x1F9FF, // Supplemental Symbols and Pictographs 🧠 🧚 🧞
    in 0x1FA70..0x1FAFF, // Symbols and Pictographs Extended-A 🪐 🪶 🪨
    in 0x1F1E6..0x1F1FF  // Flags 🇦🇨 to 🇿🇼
      -> true
    else -> false
  }
}
