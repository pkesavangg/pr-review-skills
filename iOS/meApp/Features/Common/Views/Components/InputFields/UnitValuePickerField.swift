//
//  UnitValuePickerField.swift
//  meApp
//
//  Created by Kiruthikayini S on 02/06/26.
//

import SwiftUI

// MARK: - UnitValuePickerField
/// A read-only, tappable input-style field that follows the revised unit-value field
/// structure: a **placeholder label on the left** and the **selected value on the right**,
/// followed by a chevron, all inside the standard 56pt rounded input container.
///
/// Tapping the field invokes `onTap` — used by the Height step to open the unit-aware
/// (Ft/In or CM) height picker.
struct UnitValuePickerField: View {
    @Environment(\.appTheme) private var theme
    let label: String
    let value: String
    /// Highlights the field while its picker is presented.
    var isActive: Bool = false
    var accessibilityIdentifier: String?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: .spacingXS) {
                Text(label)
                    .fontOpenSans(.subHeading1)
                    .foregroundStyle(theme.textSubheading)

                Spacer(minLength: .spacingSM)

                Text(value)
                    .fontOpenSans(.heading5)
                    .foregroundStyle(theme.textHeading)

                AppIconView(icon: AppAssets.chevronDown, size: IconSize(width: 16, height: 16))
                    .foregroundStyle(theme.statusIconSecondary)
            }
            .padding(.horizontal, .spacingSM)
            .frame(height: 56)
            .frame(maxWidth: .infinity)
            .background(theme.backgroundPrimary)
            .clipShape(.rect(cornerRadius: .radiusSM))
            .overlay {
                if isActive {
                    RoundedRectangle(cornerRadius: .radiusSM)
                        .stroke(theme.actionPrimary, lineWidth: 1.5)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier ?? label)
        .accessibilityValue(value)
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Preview
#Preview {
    VStack(spacing: 16) {
        UnitValuePickerField(label: "height", value: "6' 8\"") {}
        UnitValuePickerField(label: "height", value: "178 cm", isActive: true) {}
    }
    .padding()
    .background(Color.gray.opacity(0.2))
}
