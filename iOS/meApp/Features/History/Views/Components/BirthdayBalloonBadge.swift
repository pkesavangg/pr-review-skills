//
//  BirthdayBalloonBadge.swift
//  meApp
//

import SwiftUI

/// Small full-color balloon badge shown on the baby's birthday week/day in
/// Child History (MOB-1164). The asset bakes in the baby-product purple disc and
/// white balloon, so it is rendered `.original` (not templated) and reads well on
/// both light and dark backgrounds.
struct BirthdayBalloonBadge: View {
    var size: CGFloat = 20

    var body: some View {
        Image(AppAssets.birthdayBalloon)
            .renderingMode(.original)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityLabel(HistoryListStrings.accBirthdayBalloonLabel)
    }
}

#Preview {
    BirthdayBalloonBadge()
}
