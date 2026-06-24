//
//  DeviceManualListView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//
import SwiftUI

// MARK: - Main View
struct DeviceManualListView: View {
    @Environment(\.appTheme) private var theme
    @State private var selectedSegment: DeviceSegment = .all
    var scales: [DeviceItemInfo] = SCALES + BPMS
    var selectedScale: (DeviceItemInfo) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            ScrollView(.horizontal, showsIndicators: false) {
                SegmentedButtonView(
                    segments: DeviceSegment.allCases,
                    selectedSegment: $selectedSegment,
                    usesIntrinsicWidth: true
                )
                .padding(.horizontal, .spacingSM)
            }

            // IMPORTANT: Avoid nested vertical ScrollViews. Let parent handle vertical scrolling.
            LazyVStack(spacing: 0) {
                ForEach(filteredScales(scales, for: selectedSegment)) { scale in
                    Button {
                        selectedScale(scale)
                    } label: {
                        DeviceManualListRowView(scale: scale)
                    }
                }
            }
            .background(theme.backgroundPrimary)
            .clipShape(RoundedRectangle(cornerRadius: .spacingSM))
            .padding(.horizontal, .spacingSM)
            .padding(.bottom, .spacingXS)
        }
    }
    
    func filteredScales(_ scales: [DeviceItemInfo], for segment: DeviceSegment) -> [DeviceItemInfo] {
        switch segment {
        case .all:
            return scales
        case .bluetooth:
            return scales.filter { [.bluetooth, .lcbt, .btWifiR4, .bpm].contains($0.setupType) }
        case .wifi:
            return scales.filter { [.wifi, .espTouchWifi, .btWifiR4].contains($0.setupType) }
        case .appsync:
            return scales.filter { $0.setupType == .appSync }
        }
    }
}

#Preview(body: {
    DeviceManualListView { _ in }
})
