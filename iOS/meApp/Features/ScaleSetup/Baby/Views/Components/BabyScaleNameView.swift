//
//  BabyScaleNameView.swift
//  meApp
//

import SwiftUI

/// "Give your scale a name" — nickname input step.
struct BabyScaleNameView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: FocusField?
    private let lang = BabyScaleSetupStrings.ScaleName.self

    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)

                AppInputField(
                    config: TextInputConfig(
                        label: lang.nicknameLabel,
                        placeholder: lang.nicknamePlaceholder,
                        inputType: .text,
                        errorMessage: store.scaleNicknameForm.getError(),
                        focusField: .scaleName
                    ),
                    value: $store.scaleNicknameForm.nickname.value,
                    focusedField: focusBinding
                )
            }
            .padding(.horizontal, .spacingSM)
            .padding(.top, .spacingLG)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}
