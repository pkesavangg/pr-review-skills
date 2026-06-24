//
//  ButtonSize.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import Foundation

public enum ButtonSize {
    case large
    case small
}

public enum ButtonType: CaseIterable {
    case filledSuccess
    case filledPrimary
    case filledSecondary
    case outlinedPrimary
    case outlinedSecondary
    case textPrimary
    case textSecondary
    case textTertiary
    case inlineTextPrimary
    case inlineTextSecondary
    case inlineTextTertiary

    var label: String {
        switch self {
        case .filledSuccess: return "Filled Success"
        case .filledPrimary: return "Filled Primary"
        case .filledSecondary: return "Filled Secondary"
        case .outlinedPrimary: return "Outlined Primary"
        case .outlinedSecondary: return "Outlined Secondary"
        case .textPrimary: return "Text Primary"
        case .textSecondary: return "Text Secondary"
        case .textTertiary: return "Text Tertiary"
        case .inlineTextPrimary: return "Inline Text Primary"
        case .inlineTextSecondary: return "Inline Text Secondary"
        case .inlineTextTertiary: return "Inline Text Tertiary"
        }
    }
    
    var isInlineText: Bool {
        switch self {
        case .inlineTextPrimary, .inlineTextSecondary, .inlineTextTertiary:
            return true
        default:
            return false
        }
    }
}
