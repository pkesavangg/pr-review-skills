//
//  WeightDisplayView.swift
//  meApp
//
//  Created by Lakshmi Priya on 09/06/25.
//

import SwiftUI

struct WeightDisplayView: View {
    let weightText: String
    let unitText: String
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        HStack(alignment: .bottom, spacing: 4) {
            Text(weightText)
                .fontWeight(.heavy)
                .fontOpenSans(.heading1)
                .foregroundColor(theme.textHeading)
            Text(unitText)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .offset(y: -16)
        }
        .padding(.leading, 14)
        .padding(.trailing, 195)
        .padding(.bottom, 16)
    }
}

