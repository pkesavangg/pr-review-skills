//
//  HKIntegrationHealthAccessContent.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//


struct HKIntegrationHealthAccessContent {
    let imageName: String
    let title: String
    let description: String?
    let buttonTitle: String

    // Optional structured attributed content for better formatting
    var attributedParts: (prefix: String, highlight: String)? = nil
}

