///
///  BpmSetupMediaCard.swift
///  meApp
///

import SwiftUI

enum BpmSetupMediaMetrics {
    static let cardContentSize = CGSize(width: 332, height: 191)
}

enum BpmSetupMediaLayout {
    case top
    case center
    case bottom

    var contentAlignment: Alignment {
        switch self {
        case .top:
            return .top
        case .center:
            return .center
        case .bottom:
            return .bottom
        }
    }

    var contentPadding: EdgeInsets {
        switch self {
        case .top:
            return EdgeInsets(top: 0, leading: .spacingSM, bottom: .spacingSM, trailing: .spacingSM)
        case .center:
            return EdgeInsets(top: .spacingSM, leading: .spacingSM, bottom: .spacingSM, trailing: .spacingSM)
        case .bottom:
            return EdgeInsets(top: .spacingSM, leading: .spacingSM, bottom: 0, trailing: .spacingSM)
        }
    }

    var scaleAnchor: UnitPoint {
        switch self {
        case .top:
            return .top
        case .center:
            return .center
        case .bottom:
            return .bottom
        }
    }
}

struct BpmSetupMediaCard<Content: View>: View {
    @Environment(\.appTheme) private var theme

    var layout: BpmSetupMediaLayout = .center
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: layout.contentAlignment)
            .padding(layout.contentPadding)
        .frame(maxWidth: .infinity)
        .frame(height: DevicePlatform.isMiniPhone ? 240 : 268)
        .background(theme.backgroundPrimary)
        .clipShape(RoundedRectangle(cornerRadius: .radiusLG))
    }
}
