//  ScaleDiscoveredSheetView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/07/25.
//

import SwiftUI

// MARK: - ScaleDiscoveredSheetView
/// Half-sheet UI shown when a new A6 Bluetooth scale is discovered.
struct ScaleDiscoveredSheetView: View {
    @Environment(\.appTheme) private var theme
    
    // Callbacks to inform the presenter about dismissal / connect actions.
    let onClose: () -> Void
    let onConnect: () -> Void
    
    // Internal view-model handling timeout and disconnect logic.
    @StateObject private var viewModel: ScaleDiscoveredSheetViewModel
    
    private let commonLang = CommonStrings.self
    private let lang = ScaleDiscoveredSheetStrings.self
    
    // MARK: – Initialiser
    init(device: Device,
         discoveryEvent: DeviceDiscoveryEvent?,
         onClose: @escaping () -> Void,
         onConnect: @escaping () -> Void) {
        self.onClose = onClose
        self.onConnect = onConnect
        _viewModel = StateObject(wrappedValue: ScaleDiscoveredSheetViewModel(device: device,
                                                                             discoveryEvent: discoveryEvent,
                                                                             onTimeout: onClose))
    }
    
    // MARK: – Body
    var body: some View {
        VStack(spacing: .spacingXS) {
            // Close button – top-right aligned
            HStack {
                Spacer()
                Button {
                    viewModel.handleClose()
                } label: {
                    AppIconView(icon: AppAssets.close, size: IconSize(width: 16, height: 16))
                        .foregroundColor(theme.statusIconPrimary)
                }
                .accessibilityLabel(ScaleSetupStrings.A11y.closeButtonLabel)
                .accessibilityHint(ScaleSetupStrings.A11y.closeButtonHint)
                .appAccessibility(id: AccessibilityID.scaleDiscoveredCloseButton)
            }

            VStack(spacing: .spacingMD) {
                // Scale artwork
                if let image = viewModel.discoveryEvent?.deviceInfo.imgPath {
                    Image(image)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 175, height: 175)
                        .themeDropShadow()
                        .accessibilityHidden(true)
                }

                // Title
                VStack(spacing: .spacingXS) {
                    Text(viewModel.discoveryEvent?.deviceCategory == .bpm ? lang.bpmTitle : lang.scaleTitle)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.center)
                    if let name = viewModel.discoveryEvent?.deviceInfo.productName {
                        Text(name)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.center)
                    }
                }
                .accessibilityElement(children: .combine)

                // Connect CTA
                ButtonView(
                    text: commonLang.connect,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: false,
                    action: onConnect
                )
                .accessibilityHint(ScaleSetupStrings.A11y.connectButtonHint)
                .appAccessibility(id: AccessibilityID.scaleDiscoveredConnectButton)
                .padding(.bottom, .spacingMD)
            }
        }
        .padding([.horizontal, .top], .spacingMD)
        .frame(maxWidth: .infinity)
        .background(theme.backgroundPrimary)
        .onDisappear {
            viewModel.clearTimer()
        }
        .screenAccessibilityRoot(AccessibilityID.scaleDiscoveredSheetRoot)
    }
}

// MARK: - Local test wrapper
#if DEBUG
private struct ScaleDiscoveredSheetTestView: View {
    @State private var showSheet = false
    @Environment(\.appTheme) private var theme
    var body: some View {
        ButtonView(text: "Show Sheet", type: .filledPrimary, size: .large, isDisabled: false) {
            showSheet = true
        }
        .sheet(isPresented: $showSheet) {
            let dummyDevice = Device(
                id: UUID().uuidString,
                accountId: "dummyAccountId",
                mac: "00:11:22:33:44:55",
                deviceName: "Dummy Scale",
                broadcastId: 12345678,
                broadcastIdString: "00ABCDEF",
                isConnected: false
            )
            
            let dummyDeviceInfo = DeviceItemInfo(
                productName: "AccuCheck Verve Smart Scale",
                sku: "0412",
                imgPath: "0412",
                setupType: .btWifiR4,
                bodyComp: true
            )
            
            let dummyDiscoveryEvent = DeviceDiscoveryEvent(
                device: dummyDevice.toSnapshot(),
                deviceInfo: dummyDeviceInfo,
                protocolType: .R4,
                isNew: true
            )

            ScaleDiscoveredSheetView(
                device: dummyDevice,
                discoveryEvent: dummyDiscoveryEvent,
                onClose: { showSheet = false },
                onConnect: {}
            )
            .presentationDragIndicator(.hidden)
            .presentationCornerRadius(.radiusXL)
            .interactiveDismissDisabled(true)
            .presentationDetents([.height(400)])
        }
        .background(theme.textError)
    }
}

#Preview {
    ScaleDiscoveredSheetTestView()
        .environmentObject(Theme.shared)
}
#endif
