//
//  DeviceItemInfo.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//

import Foundation

/// Information used to render a scale row.
struct DeviceItemInfo: Identifiable, Sendable {
    let id = UUID()
    let productName: String
    let sku: String
    let imgPath: String
    let setupType: ScaleSetupType // Underlying connectivity type
    let bodyComp: Bool    // Whether the scale supports body-composition

    // BPM-specific properties (defaults safe for non-BPM callers)
    let hasNumericUsers: Bool   // true = User 1/2 labels (only 0603); false = User A/B
    let toggleButton: Bool      // true = horizontal toggle switch UI (0604, 0661)
    let hasStartButton: Bool    // false only for 0636
    let broadcastName: String?  // BLE broadcast name for matching

    init(
        productName: String,
        sku: String,
        imgPath: String,
        setupType: ScaleSetupType,
        bodyComp: Bool,
        hasNumericUsers: Bool = false,
        toggleButton: Bool = false,
        hasStartButton: Bool = true,
        broadcastName: String? = nil
    ) {
        self.productName = productName
        self.sku = sku
        self.imgPath = imgPath
        self.setupType = setupType
        self.bodyComp = bodyComp
        self.hasNumericUsers = hasNumericUsers
        self.toggleButton = toggleButton
        self.hasStartButton = hasStartButton
        self.broadcastName = broadcastName
    }
}

extension DeviceItemInfo: Equatable {
    static func == (lhs: DeviceItemInfo, rhs: DeviceItemInfo) -> Bool {
        lhs.sku == rhs.sku
    }
}
