import SwiftUI

struct ScaleLogSheetView: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var helpStore: HelpStore
    
    let scales: [Device]
    private let lang = HelpScreenStrings.self
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView<Image, EmptyView>(
                title: lang.sendScaleLog,
                leadingContent: { Image(AppAssets.xmark) },
                onLeadingTap: { dismiss() },
                canShowBorder: true,
                canShowPresentationIndicator: true
            )
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(scales) { scale in
                        VStack {
                            Button(action: {
                                helpStore.sendScaleLogHandler(device: scale)
                            }) {
                                ScaleItemView(
                                    scaleIcon: scaleIcon(for: scale.sku),
                                    modelNumber: scale.sku ?? "----",
                                    scaleName: scale.nickname ?? scale.deviceName ?? "Unknown Scale",
                                    status: scale.isConnected ?? false ? .connected : .notConnected,
                                    onTap: {
                                        helpStore.sendScaleLogHandler(device: scale)
                                    },
                                    isDisabled: !(scale.isConnected ?? false)
                                )
                            }
                            .disabled(!(scale.isConnected ?? false))
                            Divider()
                                .frame(height: 0.5)
                                .frame(maxWidth: .infinity)
                                .background(theme.statusUtilityPrimary)
                        }
                    }
                }
            }
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
    }
    
    private func scaleIcon(for sku: String?) -> Image {
        let imagePath = SCALES.first(where: { $0.sku == (sku ?? "") })?.imgPath ?? AppAssets.meLogoDark
        return Image(imagePath)
    }
}

#Preview {
    ScaleLogSheetView(scales: [])
        .environmentObject(HelpStore())
        .environmentObject(Theme.shared)
}
