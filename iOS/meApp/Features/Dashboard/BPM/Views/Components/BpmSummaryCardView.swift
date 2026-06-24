import SwiftUI

struct BpmSummaryCardView: View {
    private enum Layout {
        static let pulseColumnWidth: CGFloat = 72
        static let horizontalPadding: CGFloat = 16
        static let verticalPadding: CGFloat = 12
        static let cardHeight: CGFloat = 128
        static let valueSpacing: CGFloat = 12
        static let columnSpacing: CGFloat = 12
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
            HStack(alignment: .center, spacing: Layout.columnSpacing) {
                bpValuesSection
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(1)
                pulseSection
                    .frame(width: Layout.pulseColumnWidth, alignment: .trailing)
            }

            switch footer {
            case .centered(let title):
                Text(title)
                    .fontOpenSans(.subHeading1)
                    .foregroundColor(theme.textSubheading)
                    .frame(maxWidth: .infinity, alignment: .center)
            case .split(let left, let right):
                HStack(alignment: .center, spacing: Layout.columnSpacing) {
                    Text(left)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)
                        .frame(maxWidth: .infinity, alignment: .center)
                    Text(right)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)
                        .frame(width: Layout.pulseColumnWidth, alignment: .center)
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
                .fixedSize(horizontal: true, vertical: false)

            slashDivider

            Text("\(diastolic)")
                .fontOpenSans(.heading2)
                .fontWeight(.heavy)
                .foregroundColor(classification.color(theme: theme))
                .fixedSize(horizontal: true, vertical: false)
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
            .fixedSize(horizontal: true, vertical: false)
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
