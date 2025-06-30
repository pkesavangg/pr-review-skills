import SwiftUI

struct MetricCardView: View {
    let value: String
    let label: String
    let unit: String?
    let preLabel: String?
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        VStack(spacing: 1) {
                Text(value)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
            Text(label)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        }
        .frame(maxWidth: .infinity, minHeight: 70)
        .padding(.vertical, 12)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }
}

#Preview {
    VStack(spacing: 16) {
        MetricCardView(value: "24.5", label: "bmi", unit: nil, preLabel: nil)
        MetricCardView(value: "18.3", label: "body fat %", unit: "%", preLabel: nil)
        MetricCardView(value: "Lv. 8", label: "visceral fat", unit: nil, preLabel: "Lv.")
    }
    .padding()
    .background(Color(.systemGroupedBackground))
} 
