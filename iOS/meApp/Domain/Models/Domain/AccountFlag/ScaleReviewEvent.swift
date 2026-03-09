//
//  ScaleReviewEvent.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/09/25.
//

import Foundation

// MARK: - Scale Review Event

/// Event data for scale review notifications
public struct ScaleReviewEvent {
    public let screen: String
    public let sku: String
    public let flagId: String
    
    public init(screen: String, sku: String, flagId: String) {
        self.screen = screen
        self.sku = sku
        self.flagId = flagId
    }
}
