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
        HStack(alignment: .lastTextBaseline, spacing: 4) {
            Text(weightText)
                .fontWeight(.heavy)
                .fontOpenSans(.heading1)
                .foregroundColor(theme.textHeading)
            Text(unitText)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        }
        .padding(.leading, 14)
        .frame(height: 55)
        // React to unit changes by forcing a redraw
        .id(unitText) // This forces a redraw when the unit changes
    }
}
