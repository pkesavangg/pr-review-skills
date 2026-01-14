//
//  ScaleSetupStepOnView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/07/25.
//

import SwiftUI

struct ScaleSetupStepOnView: View {
    @Environment(\.appTheme) private var theme
    var isEntrySynced: Bool?
    private let lang = BluetoothSetupViewStrings.StepOnViewStrings.self
    private let appAssets = AppAssets.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                    
                    Text(lang.description)
                        .foregroundColor(theme.textBody)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                
 
                VStack(alignment: .center) {
                    GifView(gifName: appAssets.btStepOnGif)
                        .frame(width: DevicePlatform.isMiniPhone ? 350 : 370,
                               height: DevicePlatform.isMiniPhone ? 200 : 211)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                if let isEntrySynced = isEntrySynced {
                    Text(lang.syncingInfo(isEntrySynced))
                        .fontOpenSans(.body1)
                        .foregroundColor(theme.textBody)
                }
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    ScaleSetupStepOnView()
}
