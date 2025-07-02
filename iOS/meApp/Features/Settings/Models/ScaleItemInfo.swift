//
//  ScaleItemInfo.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//

import Foundation

/// Information used to render a scale row.
struct ScaleItemInfo: Identifiable {
    let id = UUID()
    let productName: String
    let sku: String
    let imgPath: String
    let setupType: ScaleSetupType // Underlying connectivity type
    let bodyComp: Bool    // Whether the scale supports body-composition
}
