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
                VStack(alignment: .leading) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .padding(.bottom, .spacingXS)
                    
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.step1)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                        
                        Text(lang.step2)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                        
                        Text(lang.step3.asAttributed(withBoldWords: lang.step3BoldWords))
                            .foregroundColor(theme.textBody)
                        
                        VStack(alignment: .leading) {
                            HStack(alignment: .top, spacing: 0) {
                                Text(lang.step4Number)
                                    .fontOpenSans(.body2)
                                    .foregroundColor(theme.textBody)
                                
                                VStack(alignment: .leading) {
                                    Text(lang.step4Text)
                                        .fontOpenSans(.body2)
                                        .foregroundColor(theme.textBody)
                                    
                                    if !permissionsSkipped {
                                        Text(lang.inactiveNote)
                                            .fontOpenSans(.body2)
                                            .foregroundColor(theme.textBody)
                                    }
                                }
                            }
                        }

                    }
                }
            }
            .padding(EdgeInsets(top: .spacingLG, leading: .spacingSM, bottom: 0, trailing: .spacingSM))
        }
    }
}

#Preview {
    ApModeConnectionView(connectedSSID: "connectedSSID")
}
