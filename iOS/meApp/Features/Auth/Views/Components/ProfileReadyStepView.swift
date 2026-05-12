//
//  ProfileReadyStepView.swift
//  meApp
//

import SwiftUI

struct ProfileReadyStepView: View {
    @Environment(\.appTheme) private var theme
    let title: String

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
            Spacer()
        }
        .padding(.horizontal, .spacingSM)
    }
}
