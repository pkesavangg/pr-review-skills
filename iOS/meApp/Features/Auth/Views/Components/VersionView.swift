//
//  VersionAndCopyrightView.swift
//  meApp
//
//  Created by Lakshmi Priya on 16/06/25.
//

import SwiftUI

struct VersionView: View {
    @Environment(\.appTheme) var theme
    let lang = LoadingScreenStrings.self
    private var appVersion: String {
        AppInfo.appVersion
    }
    
    var body: some View {
        VStack {
            Text("\(lang.versionPrefix.lowercased()) \(appVersion)")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textBody)
        }
        .padding(.horizontal, .spacingSM)
        .padding(.bottom, .spacingSM)
    }
}
