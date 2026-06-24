//
//  ScaleSetupFinishView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 02/07/25.
//

import SwiftUI
/// View to display the finish screen for scale setup
struct ScaleSetupFinishView: View {
    @Environment(\.appTheme) private var theme

    let title: String
    let description: String
    var isAppSyncDeviceSetup: Bool = false
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                HStack {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(title)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                            .multilineTextAlignment(.leading)
                            .lineLimit(nil)
                            .fixedSize(horizontal: false, vertical: true)

                        Text(description)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
                    .accessibilityElement(children: .combine)
                    Spacer()
                }
                AppIconView(icon: AppAssets.checkMarkLarge, size: IconSize(width: 180, height: 180))
                    .foregroundColor(theme.statusSuccess)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, .spacingXL)
                    .accessibilityLabel(ScaleSetupStrings.A11y.successIconLabel)
                if isAppSyncDeviceSetup {
                    ThemedImage(name: AppAssets.appSyncTab)
                }
            }
            .padding(.top, .spacingLG)
            .padding(.horizontal, .spacingSM)
        }
    }
}

#Preview {
    ScaleSetupFinishView(
        title: "Your scale is paired and ready to go!",
        description: "To sync new entries, tap the icon at the bottom right of the app " +
            "when you see the result code display on your scale's screen.",
        isAppSyncDeviceSetup: true
    )
    .environmentObject(Theme.shared)
}
