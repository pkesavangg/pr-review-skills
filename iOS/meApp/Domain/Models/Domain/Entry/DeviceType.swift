//
//  DeviceType.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import Foundation
/// Represents the type of device that recorded the entry.
enum DeviceType: String, Codable, Equatable, CaseIterable {
    case scale
    case bpm
    case babyScale
}
