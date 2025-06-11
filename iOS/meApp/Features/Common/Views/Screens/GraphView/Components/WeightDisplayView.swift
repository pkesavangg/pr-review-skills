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
        GeometryReader { geometry in
            HStack(alignment: .bottom, spacing: 4) {
                Text(weightText)
                    .fontWeight(.heavy)
                    .fontOpenSans(.heading1)
                    .foregroundColor(theme.textHeading)
                Text(unitText)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .alignmentGuide(.bottom) { d in d[.bottom] + 20 }
            }
            .padding(.leading, 14)
            .padding(.trailing, geometry.size.width * 0.4)
            .padding(.bottom, 16)
        }
        .frame(height: 75) 
    }
}


