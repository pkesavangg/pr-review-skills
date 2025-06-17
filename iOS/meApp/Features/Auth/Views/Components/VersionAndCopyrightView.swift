//
//  VersionAndCopyrightView.swift
//  meApp
//
//  Created by Lakshmi Priya on 16/06/25.
//

import SwiftUI

struct VersionAndCopyrightView: View {
    @Environment(\.appTheme) var theme
    let lang = LoadingScreenStrings.self
    private var appVersion: String {
        AppInfo.appVersion
    }
    
    var body: some View {
        VStack(spacing: .spacingXS) {
            Text(lang.copyright)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.backgroundPrimary)
            Text("\(lang.versionPrefix) \(appVersion)")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.backgroundPrimary)
        }
        .padding(.horizontal, .spacingSM)
        .padding(.bottom, .spacingSM)
    }
}
