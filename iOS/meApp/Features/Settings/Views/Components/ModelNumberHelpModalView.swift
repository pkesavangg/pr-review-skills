//
//  ModelNumberHelpModalView.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import SwiftUI

struct ModelNumberHelpModalView: View {
    let onClose: () -> Void
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    let lang = MyScaleStrings.self
    
    var body: some View {
        GeometryReader { geometry in
            VStack {
                
                HStack{
                    AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                        .onTapGesture {
                            onClose()
                        }
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                
                Image(AppAssets.skuNumberSticker)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 264)
                    .padding(.bottom, .spacingSM)
                
                VStack(alignment: .center, spacing: .spacingMD) {
                    Text(lang.modelNumberHelpTitle)
                        .fontOpenSans(.body2)
                        .multilineTextAlignment(.center)
                    Text(lang.modelNumberHelpExample)
                        .fontOpenSans(.body2)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(.spacingMD)
            .background(theme.backgroundSecondary)
            .cornerRadius(.radiusXL)
            .shadow(color: Color.black.opacity(0.12), radius: 10, x: 0, y: 5)
            .frame(width: geometry.size.width * 0.85)
            .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
        }
        .background(.clear)
    }
}

// MARK: - Preview
#Preview {
    ModelNumberHelpModalView(onClose: {})
        .environmentObject(Theme.shared)
}
