//  RequestIntegrationModalView.swift
//  meApp
//
import SwiftUI

// MARK: - RequestIntegrationModalView

/// Custom modal presented when the user taps "REQUEST NEW INTEGRATION".
/// Shows a title, subtitle, a free-text input field, and CANCEL / SEND actions.
/// SEND is disabled until the user types at least one character.
struct RequestIntegrationModalView: View {
    @Environment(\.appTheme) private var theme
    @FocusState private var isFieldFocused: Bool
    @State private var text: String = ""

    let onSend: (String) -> Void
    let onCancel: () -> Void

    private let strings = IntegrationsStrings.self

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingMD) {
            // Title
            Text(strings.requestIntegrationTitle)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)

            // Subtitle
            Text(strings.requestIntegrationMessage)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)

            // Text field
            TextField(strings.requestIntegrationPlaceholder, text: $text)
                .font(.body2)
                .foregroundColor(theme.textBody)
                .focused($isFieldFocused)
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingSM)
                .overlay(
                    RoundedRectangle(cornerRadius: .radiusSM)
                        .stroke(theme.glow, lineWidth: 1)
                )

            // Action buttons
            HStack(spacing: 0) {
                Spacer()
                ButtonView(
                    text: strings.requestIntegrationCancel,
                    type: .textTertiary,
                    size: .small,
                    isDisabled: false,
                    action: onCancel
                )
                ButtonView(
                    text: strings.requestIntegrationSend,
                    type: .textPrimary,
                    size: .small,
                    isDisabled: text.trimmingCharacters(in: .whitespaces).isEmpty,
                    action: { onSend(text.trimmingCharacters(in: .whitespaces)) }
                )
            }
        }
        .padding(.spacingMD)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
        .onAppear { isFieldFocused = true }
    }
}

// MARK: - Preview
#Preview {
    RequestIntegrationModalView(
        onSend: { _ in },
        onCancel: {}
    )
    .environmentObject(Theme.shared)
    .padding()
}
