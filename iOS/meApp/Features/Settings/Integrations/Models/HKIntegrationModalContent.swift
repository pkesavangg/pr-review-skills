//
//  HKIntegrationModalContent.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//

struct HKIntegrationModalContent {
    struct AttributedParts {
        let prefix: String
        let highlight: String
        let suffix: String
    }

    let imageName: String
    let title: String
    let message: String?
    var navigationPath: String?
    let primaryButtonTitle: String
    let secondaryButtonTitle: String? // Optional
    var attributedParts: AttributedParts?
}
