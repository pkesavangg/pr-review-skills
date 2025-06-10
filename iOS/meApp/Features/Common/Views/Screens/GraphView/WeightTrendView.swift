//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 06/06/25.
//

import SwiftUI

struct WeightTrendView: View {
    @State private var selectedSegment: TimePeriod = .month
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        VStack(spacing: 0) {
            // Top Section
            WeightDisplayView(weightText: "000.0", unitText: "lbs")
            
            Spacer()
                .frame(maxHeight: 10)
            
            // Segmented Control
            SegmentedButtonView(
                segments: TimePeriod.allCases,
                selectedSegment: $selectedSegment
            )
        }
        .background(theme.textInverse)
        .edgesIgnoringSafeArea(.all)
    }
}

#Preview {
    WeightTrendView()
}
