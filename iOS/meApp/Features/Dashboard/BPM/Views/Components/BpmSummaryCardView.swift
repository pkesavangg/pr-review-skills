import SwiftUI

struct BpmSummaryCardView: View {
    private enum Layout {
        static let footerLeftWidth: CGFloat = 168
        static let footerRightWidth: CGFloat = 72
        static let horizontalPadding: CGFloat = 33
        static let verticalPadding: CGFloat = 12
        static let cardHeight: CGFloat = 119
        static let valueSpacing: CGFloat = 5
    }

    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let classification: AhaPressureClass
    let footer: BpmSummaryCardFooter
    var cornerRadius: CGFloat = .radiusSM
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: .zero) {
            HStack(alignment: .center, spacing: .zero) {
                bpValuesSection
                Spacer(minLength: .zero)
                pulseSection
            }

            switch footer {
            case .centered(let title):
                Text(title)
                    .fontOpenSans(.subHeading1)
                    .foregroundColor(theme.textSubheading)
                    .frame(maxWidth: .infinity, alignment: .center)
            case .split(let left, let right):
                HStack(spacing: .zero) {
                    Text(left)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)
                        .frame(width: Layout.footerLeftWidth, alignment: .leading)
                    Spacer(minLength: .zero)
                    Text(right)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)
                        .frame(width: Layout.footerRightWidth, alignment: .leading)
                }
            }
        }
        .padding(.horizontal, Layout.horizontalPadding)
        .padding(.vertical, Layout.verticalPadding)
        .frame(maxWidth: .infinity)
        .frame(height: Layout.cardHeight)
        .background(theme.backgroundPrimary)
        .cornerRadius(cornerRadius)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
    }

    private var bpValuesSection: some View {
        HStack(alignment: .center, spacing: Layout.valueSpacing) {
            Text("\(systolic)")
                .fontOpenSans(.heading2)
                .fontWeight(.heavy)
                .foregroundColor(classification.color(theme: theme))

            slashDivider

            Text("\(diastolic)")
                .fontOpenSans(.heading2)
                .fontWeight(.heavy)
                .foregroundColor(classification.color(theme: theme))
        }
    }

    private var slashDivider: some View {
        SlashDividerView(color: theme.textSubheading.opacity(0.45))
    }

    private var pulseSection: some View {
        Text("\(pulse)")
            .fontOpenSans(.heading2)
            .fontWeight(.heavy)
            .foregroundColor(theme.textSubheading)
    }

    private var accessibilityLabel: String {
        switch footer {
        case .centered(let title):
            return "\(title), \(systolic) over \(diastolic), pulse \(pulse), \(classification.label)"
        case .split(let left, let right):
            return "\(left), \(systolic) over \(diastolic), \(right), \(pulse), \(classification.label)"
        }
    }
}
