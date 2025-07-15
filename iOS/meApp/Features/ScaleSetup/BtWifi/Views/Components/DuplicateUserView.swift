//
//  DuplicateUserView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 12/07/25.
//

import SwiftUI

struct DuplicateUserView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var store: BtWifiScaleSetupStore
    
    var isFromCustomizeSettings: Bool = false
    let scaleSetupLang = ScaleSetupStrings.self
    
    @State var focusedField: FocusField?
    let labels = InputFieldLabels.self
    
    private let lang = BtWifiScaleSetupStrings.DuplicateUserViewStrings.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(spacing: .spacingXS) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.title(isFromCustomizeSettings))
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                        
                        Text(lang.subtitle(isFromCustomizeSettings))
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textHeading)
                        
                        AppInputField(
                            config: TextInputConfig(
                                label: labels.userName,
                                inputType: .text,
                                errorMessage: store.userNameForm.getError(for: store.userNameForm.displayName),
                                focusField: .userName
                            ),
                            value: $store.userNameForm.displayName.value,
                            focusedField: $focusedField
                        ) {
                            focusedField = .userName
                        }
                        .padding(.top, .spacingSM)
                        
                        VStack {
                            if !isFromCustomizeSettings {
                                ButtonView(text: lang.restoreAccountButton, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                                    store.handleRestoreAccount()
                                }
                                if let lastActive = store.duplicateUserLastActiveAt {
                                    Text("\(lang.lastActive) \(DateTimeTools.getFormattedDateFromTimestamp(lastActive).toLowerCase())")
                                        .fontOpenSans(.subHeading2)
                                        .foregroundColor(theme.textSubheading)
                                }
                            }
                            Image(AppAssets.userInfoScreen)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 223, height: 227)
                                .padding(.top, .spacingLG)
                        }
                        .frame(maxWidth: .infinity, alignment: .center)
                        
                    }
                    .padding(.top, .spacingLG)
                }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .background(theme.backgroundSecondary)
        .navigationBarBackButtonHidden(true)
    }
}

#Preview {
    DuplicateUserView()
}
