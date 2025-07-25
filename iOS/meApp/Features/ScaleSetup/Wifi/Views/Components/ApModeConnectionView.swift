//
//  ApModeConnectionView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/07/25.
//

import SwiftUI

struct ApModeConnectionView: View {
    @Environment(\.appTheme) private var theme
    var connectedSSID: String
    var permissionsSkipped: Bool = false
    var onClickNetworkChange: (() -> Void)?
    private let lang = WifiScaleSetupStrings.ApModeConnectionViewStrings.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingSM) {
                    Text(lang.title.asAttributed(withBoldWords: lang.boldWords))
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                    
                    Text(lang.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
                
                VStack(spacing: .spacingSM) {
                    if permissionsSkipped {
                        ButtonView(
                            text: lang.gotoSettings,
                            type: .filledPrimary,
                            size: .large,
                            isDisabled: false,
                            action: {
                                onClickNetworkChange?()
                            }
                        )
                        .frame(maxWidth: .infinity, alignment: .center)
                    } else {
                        ActionListItemView(config: ActionListItemConfig(
                            title: connectedSSID.isEmpty ? lang.changeNetwork :  connectedSSID,
                            chevronType: .right,
                            onTap: {
                                onClickNetworkChange?()
                            }
                        ))
                        .padding(.horizontal, .spacingSM)
                        .background(theme.backgroundPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
                    }
                }
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    ApModeConnectionView(connectedSSID: "connectedSSID")
}
