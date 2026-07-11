package com.dmdbrands.gurus.weight.theme.enums

import androidx.compose.ui.graphics.Color

/**
 * Base color palette, aligned 1:1 with the Figma design-system base tokens
 * (Me.Health Mega App 2.0 → "Base Color" sections).
 *
 * Figma scale names (e.g. `neutral-800`, `blue-500`) are **positions in a scale, not fixed
 * lightness** — the same name resolves to a different hex in Light vs Dark. They are therefore
 * grouped under [Light] and [Dark] sub-objects, and the semantic layer ([LightColorToken] /
 * [DarkColorToken]) picks the matching theme.
 *
 * Theme-invariant brand colors (same hex in both themes) live at the top level.
 *
 * Naming maps directly to Figma: `neutral-800` → `neutral800`, `gg-secondary-900` → `ggSecondary900`,
 * `promo-red-100` → `promoRed100`. Percentage tokens keep the resolved ARGB (e.g. `neutral-300` =
 * 50% white → `0x80FFFFFF`).
 */
object ColorPalette {

  /** Base tokens for the Light theme. */
  object Light {
    // meApp · blue
    val blue100 = Color(0xFFE3F2FD)
    val blue500 = Color(0x801565C0) // 50% of #1565C0
    val blue800 = Color(0xFF1565C0)
    val blue900 = Color(0xFF11519A)

    // meApp · neutral
    val neutral100 = Color(0xFFFFFFFF)
    val neutral200 = Color(0xFFF6F4F1)
    val neutral300 = Color(0x80FFFFFF) // 50% of #FFFFFF
    val neutral400 = Color(0xFFD0CCCA)
    val neutral500 = Color(0x26000000) // 15% of #000000
    val neutral600 = Color(0x402C2827) // 25% of #2C2827
    val neutral700 = Color(0xFF7B726E)
    val neutral750 = Color(0xFF5E5653)
    val neutral800 = Color(0xFF2C2827)
    val neutral900 = Color(0xFF1F1C1B)

    // meApp · red
    val red100 = Color(0xFFF5C0BD)
    val red800 = Color(0xFFB3261E)
    val red900 = Color(0xFF8C1D18)

    // meApp · green
    val green100 = Color(0xFFB7C3B0)
    val green800 = Color(0xFF458239)
    val green900 = Color(0xFF36682D)

    // meApp · yellow / orange / teal
    val yellow100 = Color(0xFFEDB53A)
    // Advisory warning text (Balance Health / bpmMobileApp4 --ion-color-warning-text)
    val orange100 = Color(0xFFFF5F15)
    val teal100 = Color(0xFF65CEC8)

    // Greater Goods · gg-secondary
    val ggSecondary100 = Color(0x33424242) // 20% of #424242
    val ggSecondary200 = Color(0xFFA1A1A1)
    val ggSecondary800 = Color(0xFF424242)
    val ggSecondary900 = Color(0xFF2F2F2F)

    // Greater Goods · promo-red
    val promoRed100 = Color(0x33B8584E) // 20% of #B8584E
    val promoRed200 = Color(0xFFD5A6A1)
    val promoRed800 = Color(0xFFB8584E)
    val promoRed900 = Color(0xFF98483F)

    // Greater Goods · promo-blue
    val promoBlue100 = Color(0x334E738A) // 20% of #4E738A
    val promoBlue200 = Color(0xFFA7B9C3)
    val promoBlue800 = Color(0xFF4E738A)
    val promoBlue900 = Color(0xFF3F5E70)

    // Greater Goods · promo-green
    val promoGreen100 = Color(0x336E796B) // 20% of #6E796B
    val promoGreen200 = Color(0xFFB4BBB0)
    val promoGreen800 = Color(0xFF6E796B)
    val promoGreen900 = Color(0xFF5B6358)
  }

  /** Base tokens for the Dark theme. */
  object Dark {
    // meApp · blue
    val blue100 = Color(0xFF1A3959)
    val blue500 = Color(0x992B8AEB) // 60% of #2B8AEB
    val blue800 = Color(0xFF2B8AEB)
    val blue900 = Color(0xFF55A1EF)

    // meApp · neutral
    val neutral100 = Color(0xFF222D39)
    val neutral200 = Color(0xFF12161B)
    val neutral300 = Color(0x80222D39) // 50% of #222D39
    val neutral400 = Color(0xFF565F68)
    val neutral500 = Color(0x26FFFFFF) // 15% of #FFFFFF
    val neutral600 = Color(0x40E0E1E1) // 25% of #E0E1E1
    val neutral700 = Color(0xFF92989F)
    val neutral750 = Color(0xFF71767B)
    val neutral800 = Color(0xFFE0E1E1)
    val neutral900 = Color(0xFFF2F3F3)

    // meApp · red
    val red100 = Color(0xFF5C1A16)
    val red800 = Color(0xFFF28B82)
    val red900 = Color(0xFFF6B1AA)

    // meApp · green
    val green100 = Color(0xFF3C6F2F)
    val green800 = Color(0xFF63B453)
    val green900 = Color(0xFF79C66A)

    // meApp · yellow / orange / teal
    val yellow100 = Color(0xFFFDD663)
    // Advisory warning text (Balance Health / bpmMobileApp4 --ion-color-warning-text)
    val orange100 = Color(0xFFFF5F15)
    val teal100 = Color(0xFF00B3A6)

    // Greater Goods · gg-secondary
    val ggSecondary100 = Color(0x33FCF8F4) // 20% of #FCF8F4
    val ggSecondary200 = Color(0xFFBFBAB6)
    val ggSecondary800 = Color(0xFFFCF8F4)
    val ggSecondary900 = Color(0xFFE8E3DE)

    // Greater Goods · promo-red
    val promoRed100 = Color(0x33D9675C) // 20% of #D9675C
    val promoRed200 = Color(0xFF854640)
    val promoRed800 = Color(0xFFD9675C)
    val promoRed900 = Color(0xFFE3847B)

    // Greater Goods · promo-blue
    // NOTE: Figma dark promo-blue-100 is documented as "20% of #4E738A"; code preserves the
    // shipped value (20% of #839DAD). Flagged for design confirmation — see MOB-987 audit.
    val promoBlue100 = Color(0x33839DAD)
    val promoBlue200 = Color(0xFF596A76)
    val promoBlue800 = Color(0xFF839DAD)
    val promoBlue900 = Color(0xFF9AB4C3)

    // Greater Goods · promo-green
    val promoGreen100 = Color(0x339DAD99) // 20% of #9DAD99
    val promoGreen200 = Color(0xFF6B7768)
    val promoGreen800 = Color(0xFF9DAD99)
    val promoGreen900 = Color(0xFFB3C4AF)
  }

  // Theme-invariant brand colors (identical hex in Light & Dark).
  val accucheck = Color(0xFF61AD94)
  val babyScale = Color(0xFF8841A4) // baby brand color
}
