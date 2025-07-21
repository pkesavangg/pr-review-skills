//
//  WifiScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 27/06/25.
//

import SwiftUI
import Combine

struct WifiScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    let lang = WifiScreenStrings.self
    @ObservedObject var scaleStore = ScaleStore()
    @StateObject private var setupStore = BtWifiScaleSetupStore()
    
    // Scale parameter to be passed from parent view
    let scale: Device
    
    private var stepViews: [AnyView] { setupStore.stepViews }
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
                                
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews,
                shouldApplyHorizontalPadding: { index in
                    // Omit horizontal padding ONLY for the Scale Metrics customization screen.
                    !(setupStore.steps[index] == .viewSettings &&
                      setupStore.currentCustomizeSetting == .scaleMetrics)
                }
            )
            .padding(.horizontal)
            
            // Footer Buttons - show for wifiPassword step
            if setupStore.currentStep == .wifiPassword {
                footerButtons
                    .padding(.spacingSM)
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .environmentObject(setupStore)
        .onAppear {
            // Configure the setup store with the scale's SKU and set the saved scale
            setupStore.configure(with: scale.sku ?? "0412")
            setupStore.savedScale = scale
            setupStore.currentStepIndex = 4 // Index for .gatheringNetwork
        }
    }
    
    private var footerButtons: some View {
        HStack {
            ButtonView(text: CommonStrings.back,
                       type: .inlineTextPrimary,
                       size: .small,
                       isDisabled: false,
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.handleBackButtonClick()
                }
            })
            Spacer()
            ButtonView(text: setupStore.nextButtonText,
                       type: .filledPrimary,
                       size: .small,
                       isDisabled: !setupStore.isNextEnabled,
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.handleNextButtonClick()
                }
            })
        }
    }
  
}
