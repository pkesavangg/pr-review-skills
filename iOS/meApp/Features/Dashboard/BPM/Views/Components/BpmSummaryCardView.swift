import SwiftUI

struct BpmSummaryCardView: View {
    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let classification: AhaPressureClass
    let footer: Footer
    var cornerRadius: CGFloat = 9
    @Environment(\.appTheme) private var theme

    enum Footer {
        case centered(String)
        case split(left: String, right: String)
    }

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
                        .frame(width: 168, alignment: .leading)
                    Spacer(minLength: .zero)
                    Text(right)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)
                        .frame(width: 72, alignment: .leading)
                }
            }
        }
        .padding(.horizontal, 33)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .frame(height: 119)
        .background(theme.backgroundPrimary)
        .cornerRadius(cornerRadius)
    }

    private var bpValuesSection: some View {
        HStack(alignment: .center, spacing: 5) {
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
        RoundedRectangle(cornerRadius: 0.5)
            .fill(theme.textSubheading.opacity(0.45))
            .frame(width: 1, height: 47)
            .rotationEffect(.degrees(15))
    }

    private var pulseSection: some View {
        Text("\(pulse)")
            .fontOpenSans(.heading2)
            .fontWeight(.heavy)
            .foregroundColor(theme.textSubheading)
    }
}
