//
//  ProfileReadyStepView.swift
//  meApp
//

import SwiftUI

struct ProfileReadyStepView: View {
    @Environment(\.appTheme) private var theme
    let title: String
    var completedDevices: [SignupDeviceType] = []

    var body: some View {
        VStack(spacing: .spacingLG) {
            Spacer()
            AppIconView(icon: AppAssets.checkMarkLarge, size: IconSize(width: 180, height: 180))
                .foregroundColor(theme.statusSuccess)
                .frame(maxWidth: .infinity, alignment: .center)
            Text(title)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, alignment: .center)
            if completedDevices.count >= 2 {
                HStack(spacing: .spacingLG) {
                    ForEach(completedDevices, id: \.id) { device in
                        Image(device.iconName)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 60, height: 60)
                    }
                }
            }
            Spacer()
        }
        .padding(.horizontal, .spacingSM)
    }
}
