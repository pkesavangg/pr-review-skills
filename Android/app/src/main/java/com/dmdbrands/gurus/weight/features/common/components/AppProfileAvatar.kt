package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.strings.AppProfileAvatarStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

// Width ratio for the combined letter + profile icon variant (Figma node 7453:121594)
private const val INFO_ICON_WIDTH_RATIO = 1.7f
// Profile icon is ~44% of the avatar size per Figma spec (24dp at size=55dp)
private const val PROFILE_ICON_SIZE_RATIO = 0.44f
// Border / inset width used for the info icon variant circles (Figma spec).
// `Dp` is not a primitive, so `const val` is not applicable here.
private val BORDER_WIDTH_DP = 3.dp
// Optical nudge that compensates for the profile glyph's geometric asymmetry within its viewport.
private val ICON_OPTICAL_NUDGE = 2.dp

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
    contentDescription: String? = null,
    onLongPress: (() -> Unit)? = null,
) {
    // Account avatar is the WG brand blue per Figma (Account Switch icon): active = solid wgPrimary
    // with white initial, inactive = wgPrimary outline + wgPrimary initial. iconPrimary was remapped
    // to neutral #2C2827 in MOB-987, which had turned the avatar dark. (MOB-1259)
    val backgroundColor = when {
        isActive -> MeTheme.colorScheme.wgPrimary
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> MeTheme.colorScheme.primaryActionDisabled
        isActive -> MeTheme.colorScheme.inverseAction
        else -> MeTheme.colorScheme.wgPrimary
    }
    val borderModifier = when {
        !isActive && enabled -> Modifier.border(2.dp, MeTheme.colorScheme.wgPrimary, CircleShape)
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
        SingleAvatar(text, modifier, size, backgroundColor, textColor, borderModifier, gestureModifier, contentDescription)
    } else {
        InfoIconAvatar(text, modifier, size, backgroundColor, textColor, gestureModifier, contentDescription)
    }
}

/**
 * Default single circular avatar showing the first non-emoji character of [text].
 */
@Composable
private fun SingleAvatar(
    text: String,
    modifier: Modifier,
    size: Dp,
    backgroundColor: Color,
    textColor: Color,
    borderModifier: Modifier,
    gestureModifier: Modifier,
    contentDescription: String?,
) {
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
            // TalkBack: the single initial is meaningless on its own (usually the
            // full name is shown next to the avatar). Hide it by default; announce
            // a caller-provided label only when the avatar stands alone.
            modifier = Modifier.clearAndSetSemantics {
                contentDescription?.let { this.contentDescription = it }
            },
        )
    }
}

/**
 * Combined name-initial + profile-icon avatar variant (Figma node 7453:121594).
 */
@Composable
private fun InfoIconAvatar(
    text: String,
    modifier: Modifier,
    size: Dp,
    backgroundColor: Color,
    textColor: Color,
    gestureModifier: Modifier,
    contentDescription: String?,
) {
    // Combined name initial letter avatar + profile icon
    Box(
        modifier = modifier
            .width(size * INFO_ICON_WIDTH_RATIO)
            .height(size)
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        AvatarProfileIcon(size = size)
        AvatarLetterCircle(
            text = text,
            size = size,
            backgroundColor = backgroundColor,
            textColor = textColor,
            contentDescription = contentDescription,
        )
    }
}

/**
 * Profile-icon circle for the info-icon avatar, overlapping on the right (lower z-index).
 */
@Composable
private fun BoxScope.AvatarProfileIcon(size: Dp) {
    // Profile icon - overlapping on the right (rendered first, lower z-index).
    // Info icon variant is only used in AccountSwitchInfoModal where the avatar
    // is always active/enabled, so isActive/enabled states are intentionally
    // not reflected here.
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .size(size)
            .border(BORDER_WIDTH_DP, MeTheme.colorScheme.wgPrimary, CircleShape)
            .clip(CircleShape)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            id = AppIcons.Filled.Profile,
            contentDescription = AppProfileAvatarStrings.accProfileLabel,
            // Person glyph is the WG brand blue to match the avatar outline. (MOB-1259)
            tintColor = MeTheme.colorScheme.wgPrimary,
            modifier = Modifier
                // Optical nudge compensates for the SVG path's geometric asymmetry so the
                // glyph appears centered inside the border circle (per Figma).
                // `absolutePadding` keeps the nudge in a fixed direction relative to the
                // path geometry across LTR and RTL layouts, since the path itself is not
                // auto-mirrored.
                .absolutePadding(left = ICON_OPTICAL_NUDGE)
                .align(Alignment.Center)
                .size(size * PROFILE_ICON_SIZE_RATIO),
        )
    }
}

/**
 * Letter circle for the info-icon avatar, positioned on the left (higher z-index).
 */
@Composable
private fun BoxScope.AvatarLetterCircle(
    text: String,
    size: Dp,
    backgroundColor: Color,
    textColor: Color,
    contentDescription: String?,
) {
    // Letter box - positioned on the left (rendered second, higher z-index)
    // Using padding(3.dp) instead of border() to avoid the background bleeding
    // past the circle edge and forming a ring-like outline. Size matches Figma.
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .padding(BORDER_WIDTH_DP),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.firstOrNull()?.uppercase() ?: "",
            style = MeTheme.typography.heading4,
            color = textColor,
            // Decorative initial (see non-info variant note).
            modifier = Modifier.clearAndSetSemantics {
                contentDescription?.let { this.contentDescription = it }
            },
        )
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

@PreviewTheme
@Composable
fun AppProfileInfoIconPreview() {
    MeAppTheme {
        AppProfileAvatar(
            text = "Kevin",
            size = 55.dp,
            isInfoIcon = true,
            isActive = true,
            enabled = true,
        )
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
