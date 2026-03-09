//
//  ScaleSegment.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//

/// Enumeration for the segmented control.
enum ScaleSegment: String, CaseIterable, Identifiable {
    case all       = "All"
    case bluetooth = "Bluetooth"
    case wifi      = "WiFi"
    case appsync   = "AppSync"

    var id: String { rawValue }
}
