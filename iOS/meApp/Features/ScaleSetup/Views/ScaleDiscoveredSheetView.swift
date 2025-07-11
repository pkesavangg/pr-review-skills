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
        VStack(spacing: .spacingSM) {
            // Close button – top-right aligned
            HStack {
                Spacer()
                Button {
                    viewModel.handleClose()
                } label: {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 22, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
            .padding(.top, .spacingSM)
            .padding(.horizontal, .spacingSM)
            
            VStack(spacing: .spacingMD) {
                // Scale artwork
                if let image = viewModel.discoveryEvent?.deviceInfo.imgPath {
                    Image(image)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 150, height: 150)
                        .dropShadow(DropShadow.glowBlack)
                        .padding(.bottom, .spacingXS)
                }
                
                // Title
                VStack(spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.center)
                    if let name = viewModel.device.nickname {
                        Text(name)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.center)
                    }
                }
                
                // Connect CTA
                ButtonView(
                    text: commonLang.connect,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: false,
                    action: onConnect
                )
                .padding(.horizontal, .spacingLG)
            }
            Spacer(minLength: .spacingLG)
        }
        .padding(.spacingXS)
        .frame(maxWidth: .infinity)
        .background(theme.backgroundSecondary)
    }
}

// MARK: - Local test wrapper
#if DEBUG
private struct ScaleDiscoveredSheetTestView: View {
    @State private var showSheet = false
    
    var body: some View {
        ButtonView(text: "Show Sheet", type: .filledPrimary, size: .large, isDisabled: false) {
            showSheet = true
        }
        .sheet(isPresented: $showSheet) {
            // For testing we use a dummy device
            let dummyDevice = Device(id: "test", accountId: "", mac: nil, deviceName: "Test", broadcastId: 0, broadcastIdString: "00", isConnected: false)
            ScaleDiscoveredSheetView(device: dummyDevice, discoveryEvent: nil, onClose: { showSheet = false }, onConnect: {})
                .presentationDetents([.fraction(0.5)])
                .presentationDragIndicator(.hidden)
                .presentationCornerRadius(.radiusXL)
                .interactiveDismissDisabled(true)
        }
        .padding()
        .background(Color.black.opacity(0.1))
    }
}

#Preview {
    ScaleDiscoveredSheetTestView()
        .environmentObject(Theme.shared)
}
#endif
