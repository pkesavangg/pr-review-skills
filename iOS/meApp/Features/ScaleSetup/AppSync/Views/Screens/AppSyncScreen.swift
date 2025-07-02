//
//  AppSyncScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//

import SwiftUI

struct AppSyncScreen: View {
    @StateObject private var setupStore = AppSyncSetupStore()
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) var dismiss
    let sku: String
    var commonLang = CommonStrings.self
    var isFromAccountSwitching: Bool = false

    /// Lookup `ScaleItemInfo` for the provided SKU.
    private var scaleItem: ScaleItemInfo { SCALES.first { $0.sku == sku }  ?? SCALES[0] }

    private var stepViews: [AnyView] {
        [
            // 1. Scale info
            AnyView(ScaleSetupInfoView(scale: scaleItem)),
            // 2. Permissions
            AnyView(PermissionListView(categories: [.camera])),
            AnyView(ActivateYourScaleView()),
            AnyView(AddInfoView()),   // addInfo
            AnyView(TimeToWeighView()),   // timeToWeigh
            AnyView(EmptyView()),   // appSync
            AnyView(EmptyView())    // finish
        ]
    }
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: "Scale Setup - \(sku)",
                leadingContent: {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 25, height: 22))
                        .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: {
                    Button {
                        setupStore.showHelpModal()
                    } label: {
                        AppIconView(icon: AppAssets.helpCircle)
                            .foregroundColor(theme.statusIconPrimary)
                    }
                },
                onLeadingTap: {
                    setupStore.handleExit()
                },
                onTrailingTap: {},
                canShowPresentationIndicator: true
            )
            
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews
            )
            // Footer Buttons
            footerButtons
                .padding(.spacingSM)
            
        }
        .onAppear {
            setupStore.dismissAction = dismiss
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary)
    }
    
    private var footerButtons: some View {
        HStack {
            ButtonView(text: commonLang.back,
                       type: .inlineTextPrimary,
                       size: .small,
                       isDisabled: setupStore.currentStepIndex == 0,
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.moveToPreviousStep()
                }
            })
            
            Spacer()
            
            ButtonView(text: setupStore.currentStepIndex == setupStore.steps.count - 1 ? commonLang.complete : commonLang.next,
                       type: .filledPrimary,
                       size: .small,
                       isDisabled: !setupStore.isNextEnabled,
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.moveToNextStep()
                }
            })
        }
    }
}

#Preview {
    AppSyncScreen(sku: "0343")
}
