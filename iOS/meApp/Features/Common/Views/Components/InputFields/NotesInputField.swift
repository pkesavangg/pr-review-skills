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
            VStack(alignment: .leading, spacing: 0) {
                ZStack(alignment: .topLeading) {
                    TextEditor(text: $value)
                        .font(.body1)
                        .foregroundColor(theme.textBody)
                        .scrollContentBackground(.hidden)
                        .padding(.horizontal, CGFloat.spacingXS)
                        .padding(.top, CGFloat.spacingXS)
                        .frame(height: 100)
                        .onTapGesture {
                            focusedField = config.focusField
                        }
                        .onChange(of: value) { _, newValue in
                            if newValue.count > maxCharacters {
                                value = String(newValue.prefix(maxCharacters))
                            }
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

                Rectangle()
                    .fill(theme.statusUtilityPrimary)
                    .frame(height: 1)

                HStack {
                    Spacer()
                    Text("\(value.count)/\(maxCharacters)")
                        .fontOpenSans(.body4)
                        .foregroundStyle(
                            value.count >= maxCharacters
                                ? theme.textError
                                : theme.textSubheading
                        )
                        .padding(.trailing, CGFloat.spacingXS + 4)
                        .padding(.vertical, 6)
                }
            }
            .background(theme.backgroundSecondary)
            .cornerRadius(BorderRadius.sm)

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
