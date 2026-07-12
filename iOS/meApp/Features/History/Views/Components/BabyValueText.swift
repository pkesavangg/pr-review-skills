//
//  BabyValueText.swift
//  meApp
//

import SwiftUI

/// Renders a baby-metric value string (e.g. "8 lb 14.9 oz", "12 in", "6 th") with the numeric
/// tokens in the baby-product purple heading style and the unit tokens in the smaller body style,
/// matching the Figma spec:
/// - number: `gG-Baby` (#8841A4), Open Sans 16 / 700  → `babyScaleColor` + `.heading5`
/// - unit:   meApp `Text-body` (#2C2827), Open Sans 14 / 400 → `textBody` + `.body3`
///
/// Pass `onDarkBackground` for the expanded/highlighted row where the text must invert.
struct BabyValueText: View {
    @Environment(\.appTheme) private var theme

    let value: String
    var onDarkBackground: Bool = false

    var body: some View {
        styledText
    }

    private var styledText: Text {
        let numberColor = onDarkBackground ? theme.textInverse : theme.babyScaleColor
        let unitColor = onDarkBackground ? theme.actionInverseSecondary : theme.textBody

        // Values are space-separated number/unit tokens ("8 lb 14.9 oz", "12 in", "6 th").
        // Numeric tokens get the purple heading style; everything else (units, "--", ordinals)
        // gets the smaller body style so the units read as secondary, per the design.
        return value.split(separator: " ").enumerated().reduce(Text("")) { result, pair in
            let (index, token) = pair
            let isNumber = Double(token) != nil
            let piece = Text(index == 0 ? String(token) : " \(token)")
                .fontOpenSans(isNumber ? .heading5 : .body3)
                .foregroundStyle(isNumber ? numberColor : unitColor)
            return result + piece
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 8) {
        BabyValueText(value: "8 lb 14.9 oz")
        BabyValueText(value: "12 in")
        BabyValueText(value: "6 th")
    }
    .padding()
}
