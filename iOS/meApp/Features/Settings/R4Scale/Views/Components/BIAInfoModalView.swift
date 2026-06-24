//
//  BIAInfoModalView.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct BIAInfoModalView: View {
    let onClose: () -> Void
    @Environment(\.appTheme) private var theme
    let lang = DeviceModesStrings.self

    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                
                HStack {
                    Spacer()
                    Image(AppAssets.xmarkSmall)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 20, height: 20)
                        .onTapGesture {
                            onClose()
                        }
                }

                Text(lang.biaInfoTitle)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.center)
                    .fontWeight(.bold)
                    .padding(.bottom, .spacingSM)

                VStack(alignment: .center, spacing: .spacingSM) {
                    Text(lang.biaInfoPara1)
                        .fontOpenSans(.body2)
                        .multilineTextAlignment(.center)
                    Text(lang.biaInfoPara2)
                        .fontOpenSans(.body2)
                        .multilineTextAlignment(.center)
                    Text(lang.biaInfoPara3)
                        .fontOpenSans(.body2)
                        .multilineTextAlignment(.center)
                }
                .foregroundColor(theme.textBody)
                .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.spacingMD)
            .background(theme.backgroundSecondary)
            .cornerRadius(.radiusXL)
            .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
        }
        .background(.clear)
    }
}

// MARK: - Preview
#Preview {
    BIAInfoModalView {}
        .environmentObject(Theme.shared)
}
