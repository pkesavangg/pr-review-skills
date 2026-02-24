//
//  HKIntegrationModalContent.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//

struct HKIntegrationModalContent {
    let imageName: String
    let title: String
    let message: String?
    var navigationPath: String?
    let primaryButtonTitle: String
    let secondaryButtonTitle: String? // Optional

    // For structured rich text rendering
// swiftlint:disable:next large_tuple
    var attributedParts: (prefix: String, highlight: String, suffix: String)?
}
