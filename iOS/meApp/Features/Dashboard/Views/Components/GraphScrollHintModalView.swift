//
//  GraphScrollHintModalView.swift
//  meApp
//
//  First-time discoverability hint that teaches users the weight-trend graph
//  is scrollable. Designed to be shown via NotificationHelperService.showModal.
//

import SwiftUI

struct GraphScrollHintModalView: View {
    let onClose: () -> Void

    @Environment(\.appTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    /// Aspect ratio (W:H) of the source GIF (848 × 612), which also matches the
    /// chart-card slot in the Figma design (212 × 153).
    private let demoAspectRatio: CGFloat = 848.0 / 612.0
    private let demoCornerRadius: CGFloat = .radiusSM

    var body: some View {
        VStack(spacing: 0) {
            closeButtonRow

            VStack(spacing: .spacingLG) {
                VStack(spacing: .spacingSM) {
                    animatedDemo
                        .aspectRatio(demoAspectRatio, contentMode: .fit)
                        .padding(.horizontal, .spacingMD)

                    VStack(spacing: .spacingSM) {
                        Text(DashboardStrings.graphScrollHintTitle)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                            .multilineTextAlignment(.center)

                        Text(DashboardStrings.graphScrollHintMessage)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.center)
                    }
                }

                ButtonView(
                    text: DashboardStrings.graphScrollHintConfirm,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: false,
                    action: onClose
                )
            }
        }
        .padding(.spacingMD)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusXL)
        .shadow(color: Color.black.opacity(0.12), radius: 10, x: 0, y: 5)
    }

    // MARK: - Subviews

    private var closeButtonRow: some View {
        HStack(spacing: 0) {
            Button(action: onClose) {
                AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 20, height: 20))
                    .foregroundColor(theme.actionPrimary)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.bottom, .spacingXS)
    }

    private var animatedDemo: some View {
        GeometryReader { geo in
            GifView(
                gifName: AppAssets.graphScrollHintGif(colorScheme == .dark),
                width: geo.size.width,
                height: geo.size.height
            )
            .clipShape(RoundedRectangle(cornerRadius: demoCornerRadius))
        }
    }
}

// MARK: - Preview

#Preview {
    GraphScrollHintModalView(onClose: {})
        .environmentObject(Theme.shared)
        .padding()
        .background(Color.gray.opacity(0.3))
}
