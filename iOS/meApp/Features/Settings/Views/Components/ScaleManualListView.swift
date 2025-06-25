//
//  ScaleManualListView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//
import SwiftUI

// MARK: - Main View
struct ScaleManualListView: View {
    @Environment(\.appTheme) private var theme
    @State private var selectedSegment: ScaleSegment = .all
    var scales: [ScaleItemInfo] = SCALES
    var selectedScale: (ScaleItemInfo) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            ScrollView(.horizontal, showsIndicators: false) {
                SegmentedButtonView(segments: ScaleSegment.allCases, selectedSegment: $selectedSegment)
                    .padding(.horizontal, .spacingSM)
            }

            // Vertical scroll ONLY for the list
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 0) {
                    ForEach(filteredScales(scales, for: selectedSegment)) { scale in
                        Button {
                            selectedScale(scale)
                        } label: {
                             ScaleManualListRowView(scale: scale)
                        }
                    }
                }
            }
        }
    }
    
    func filteredScales(_ scales: [ScaleItemInfo], for segment: ScaleSegment) -> [ScaleItemInfo] {
        switch segment {
        case .all:
            return scales
        case .bluetooth:
            return scales.filter { [.bluetooth, .lcbt, .btWifiR4].contains($0.setupType) }
        case .wifi:
            return scales.filter { [.wifi, .espTouchWifi, .btWifiR4].contains($0.setupType) }
        case .appsync:
            return scales.filter { $0.setupType == .appSync }
        }
    }
}


#Preview(body: {
    ScaleManualListView() { scale in
        print("Selected scale: \(scale)")
    }
})
