//
//  SectionHeader.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import SwiftUI

struct SectionHeader: View {
    let title: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(title)
            .fontOpenSans(.heading4)
            .foregroundColor(theme.textHeading)
            .textCase(.none)
            .padding(.bottom, .spacingXS)
            .padding(.leading, -16)
    }
}
