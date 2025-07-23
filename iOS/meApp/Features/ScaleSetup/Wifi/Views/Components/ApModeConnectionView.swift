//
//  ApModeConnectionView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/07/25.
//

import SwiftUI

struct ApModeConnectionView: View {
    @Environment(\.appTheme) private var theme
    var connectedSSID: String?
    var onClickNetworkChange: (() -> Void)?
    private let lang = WifiScaleSetupStrings.ApModeConnectionViewStrings.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingSM) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                    
                    Text(lang.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
                
                VStack(spacing: .spacingSM) {
                    ActionListItemView(config: ActionListItemConfig(
                        title: connectedSSID ?? lang.changeNetwork,
                        chevronType: connectedSSID == nil ? .right : .none,
                        onTap: {
                            if connectedSSID != nil {
                                onClickNetworkChange?()
                            }
                        }
                    ))
                    .padding(.horizontal, .spacingSM)
                    .background(theme.backgroundPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
                }
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    ApModeConnectionView()
}
