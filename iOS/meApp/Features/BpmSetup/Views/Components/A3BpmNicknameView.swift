///
///  A3BpmNicknameView.swift
///  meApp
///

import SwiftUI

/// View for the ``BpmSetupStep/nickname`` step.
/// Lets the user name the paired BPM device.
struct A3BpmNicknameView: View {
    @Environment(\.appTheme) private var theme

    @Binding var nickname: String
    @Binding var focusedField: FocusField?

    private let lang = BpmSetupStrings.Nickname.self

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingLG) {
            Text(lang.title)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .multilineTextAlignment(.leading)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)

            AppInputField(
                config: TextInputConfig(
                    label: lang.placeholder,
                    submitLabel: .done
                ),
                value: $nickname,
                focusedField: $focusedField,
                accessibilityIdentifier: AccessibilityID.bpmNicknameField
            ) {
                focusedField = nil
            }

            Spacer()
        }
        .padding(.top, .spacingLG)
        .padding(.horizontal, .spacingSM)
    }
}

#Preview {
    A3BpmNicknameView(
        nickname: .constant("Smart Wrist Blood Pressure Monitor"),
        focusedField: .constant(nil)
    )
    .padding(.horizontal)
    .environmentObject(Theme.shared)
}
