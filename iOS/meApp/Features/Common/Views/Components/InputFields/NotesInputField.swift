//
//  NotesInputField.swift
//  meApp
//

import SwiftUI

/// A reusable multiline text input field with placeholder label support.
///
/// Follows the same `TextInputConfig` pattern as `AppInputField` and `MetricInputField`.
/// Renders a `TextEditor` with a themed placeholder that hides when text is entered.
/// Enforces a 280-character limit and shows a live counter at the bottom-right (MOB-437).
struct NotesInputField: View {
    @Environment(\.appTheme) private var theme

    var config: TextInputConfig
    @Binding var value: String
    @Binding var focusedField: FocusField?

    private let maxCharacters = AppConstants.Input.notesMaxCharacters

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                TextEditor(text: $value)
                    .font(.body1)
                    .foregroundColor(theme.textBody)
                    .scrollContentBackground(.hidden)
                    .padding(.horizontal, CGFloat.spacingXS)
                    .padding(.top, CGFloat.spacingXS)
                    .padding(.bottom, 24) // reserve space so text doesn't hide under the counter
                    .frame(minHeight: 100)
                    .background(theme.backgroundPrimary)
                    .cornerRadius(BorderRadius.sm)
                    .onTapGesture {
                        focusedField = config.focusField
                    }
                    .onChange(of: value) { _, newValue in
                        if newValue.count > maxCharacters {
                            value = String(newValue.prefix(maxCharacters))
                        }
                    }
                    .overlay(alignment: .bottomTrailing) {
                        Text("\(value.count)/\(maxCharacters)")
                            .fontOpenSans(.body4)
                            .foregroundStyle(
                                value.count >= maxCharacters
                                    ? theme.textError
                                    : theme.textSubheading
                            )
                            .padding(.trailing, CGFloat.spacingXS + 4)
                            .padding(.bottom, CGFloat.spacingXS + 2)
                    }

                if value.isEmpty {
                    Text(config.label)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .padding(.horizontal, CGFloat.spacingXS + 4)
                        .padding(.vertical, CGFloat.spacingXS + 8)
                        .allowsHitTesting(false)
                }
            }

            if let errorMessage = config.errorMessage {
                Text(errorMessage)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textError)
                    .padding(.leading, .spacingSM)
                    .padding(.top, 4)
            }
        }
    }
}
