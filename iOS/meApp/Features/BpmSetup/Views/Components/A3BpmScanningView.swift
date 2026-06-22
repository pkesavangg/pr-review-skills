///
///  A3BpmScanningView.swift
///  meApp
///

import SwiftUI

/// View for the ``BpmSetupStep/scanning`` step.
/// Shows a pulsing connection indicator while scanning, or failure state with retry.
struct A3BpmScanningView: View {
    @Environment(\.appTheme) private var theme

    let connectionState: ConnectionState
    let bpmItem: ScaleItemInfo
    let onTryAgain: () -> Void
    let onSupport: () -> Void

    private let lang = BpmSetupStrings.Scanning.self

    var body: some View {
        if connectionState == .failure {
            BluetoothConnectionView(
                state: .failure,
                setupType: .bluetooth,
                onTryAgain: onTryAgain,
                onSupport: onSupport
            )
        } else if let gifName = syncingGifName {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: .spacingLG) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.title)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                            .multilineTextAlignment(.leading)
                            .lineLimit(nil)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    BpmSetupMediaCard(layout: .center) {
                        GifView(
                            gifName: gifName,
                            subdirectory: bpmMediaSubdirectory
                        )
                        .accessibilityHidden(true)
                    }
                    .padding(.horizontal, 0)
                    .frame(maxWidth: .infinity, alignment: .center)
                }
                .padding(.top, .spacingLG)
            }
        } else {
            ConnectionPromptView(
                title: lang.title,
                image: AppAssets.bluetooth,
                scaleImagePath: bpmItem.imgPath
            )
        }
    }

    private var bpmMediaSubdirectory: String? {
        if a3BpmSkus.contains(bpmItem.sku) {
            return BpmA3MonitorSetupAssets.gifBundleSubdirectory(for: bpmItem.sku)
        }
        if a6BpmSkus.contains(bpmItem.sku) {
            return BpmA6MonitorSetupAssets.gifBundleSubdirectory(for: bpmItem.sku)
        }
        return nil
    }

    private var syncingGifName: String? {
        if a3BpmSkus.contains(bpmItem.sku) {
            return BpmA3MonitorSetupAssets.resourceName(BpmA3MonitorSetupAssets.ImageFile.syncing)
        }
        if a6BpmSkus.contains(bpmItem.sku) {
            return BpmA6MonitorSetupAssets.resourceName(BpmA6MonitorSetupAssets.ImageFile.syncing)
        }
        return nil
    }
}
