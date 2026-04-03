import SwiftUI

struct GraphSelectionDateCalloutView: View {
    let label: String
    let theme: AppColors.Palette
    let xPosition: CGFloat

    var body: some View {
        Text(label)
            .fontOpenSans(.subHeading2)
            .foregroundColor(theme.textSubheading)
            .position(x: xPosition, y: -15)
    }
}

struct BabyPercentileCalloutView: View {
    let percentile: Int
    let theme: AppColors.Palette
    let topPadding: CGFloat

    var body: some View {
        Text("\(percentile)%")
            .fontOpenSans(.subHeading2)
            .foregroundColor(theme.textSubheading)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.leading, 8)
            .padding(.top, topPadding)
    }
}

struct GoalWeightChipView: View {
    let label: String
    let theme: AppColors.Palette

    var body: some View {
        Text(label)
            .fontWeight(.bold)
            .fontOpenSans(.body3)
            .foregroundColor(theme.actionInverse)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(Capsule().fill(theme.statusSuccess))
    }
}
