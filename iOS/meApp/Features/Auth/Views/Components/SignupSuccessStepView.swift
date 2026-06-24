//
//  SignupSuccessStepView.swift
//  meApp
//

import SwiftUI

struct SignupSuccessStepView: View {
    @Environment(\.appTheme) private var theme
    let deviceTypes: [SignupDeviceType]
    let lang = SignupStrings.AllProfilesReadyStep.self

    var body: some View {
        VStack(spacing: .spacingLG) {
            Spacer()
            AppIconView(icon: AppAssets.checkMarkLarge, size: IconSize(width: 180, height: 180))
                .foregroundColor(theme.statusSuccess)
                .frame(maxWidth: .infinity, alignment: .center)
                .accessibilityHidden(true)
            Text(lang.title)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, alignment: .center)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: .spacingLG) {
                ForEach(deviceTypes, id: \.id) { deviceType in
                    Image(deviceType.iconName)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 60, height: 60)
                        .accessibilityLabel(deviceType.title)
                }
            }
            Spacer()
        }
        .padding(.horizontal, .spacingSM)
    }
}
