//
//  CustomTextStyle.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import SwiftUI

// MARK: - Custom Text Style Enum
enum CustomTextStyle: CaseIterable {
    case heading1
    case heading2
    case heading3
    case heading4
    case heading5
    case subHeading1
    case subHeading2
    case body1
    case body2
    case body3
    case body4
    case body5
    case link1
    case link2
    case button1
    case button2
    case label1
    case itemTitle

    var size: CGFloat {
        switch self {
        case .heading1: return 60
        case .heading2: return 50
        case .heading3: return 36
        case .heading4: return 24
        case .heading5: return 16
        case .subHeading1: return 16
        case .subHeading2: return 14
        case .body1: return 20
        case .body2: return 16
        case .body3: return 14
        case .body4: return 12
        case .body5: return 10
        case .link1: return 16
        case .link2: return 12
        case .button1: return 16
        case .button2: return 14
        case .label1: return 14
        case .itemTitle: return 46
        }
    }

    var weight: Font.Weight {
        switch self {
        case .heading1, .heading2: return .heavy // Extra Bold
        case .heading3, .heading4, .heading5: return .bold
        case .subHeading1, .subHeading2: return .regular
        case .body1, .body2, .body3, .body4, .body5: return .regular
        case .link1, .link2: return .semibold
        case .button1, .button2: return .semibold
        case .label1: return .bold
        case .itemTitle: return .regular
        }
    }
}
