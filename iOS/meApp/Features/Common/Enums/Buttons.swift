//
//  ButtonSize.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import Foundation

public enum ButtonSize {
    case regular
    case small
}

public enum ButtonType: CaseIterable {
    case primary
    case secondary
    case primaryInverse
    case secondaryInverse
    case linkBlueDefault
    case linkBlueInline
    case linkWhiteDefault
    case linkWhiteInline
    case smallTertiaryLink

    var label: String {
        switch self {
        case .primary: return "Primary"
        case .secondary: return "Secondary"
        case .primaryInverse: return "Primary Inverse"
        case .secondaryInverse: return "Secondary Inverse"
        case .linkBlueDefault: return "Link Blue Default"
        case .linkBlueInline: return "Link Blue Inline"
        case .linkWhiteDefault: return "Link White Default"
        case .linkWhiteInline: return "Link White Inline"
        case .smallTertiaryLink: return "Small Tertiary Link"
        }
    }
}
