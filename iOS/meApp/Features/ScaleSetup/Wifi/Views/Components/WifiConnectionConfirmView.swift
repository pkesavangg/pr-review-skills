//
//  WifiConnectionConfirmView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/07/25.
//

import SwiftUI

struct WifiConnectionConfirmView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    let sku: String
    let userNumber: Int?
    let selectedOption: WifiSetupOption
    let mode: WifiConnectionConfirmMode
    let onOptionSelected: ((WifiSetupOption) -> Void)?
    let onClickButton: (() -> Void)?
    
    private let wifiSetuplang = WifiScaleSetupStrings.self
    private let lang = WifiScaleSetupStrings.UserConfirmationViewStrings.self
    private let appAssets = AppAssets.self
    private let sku0384 = "0384"
    private let sku0396 = "0396"
    private let imageSize120: CGFloat = 120
    private let imageSize100: CGFloat = 100
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                contentView
                noteSection
                buttonSection
            }
            .padding(.top, .spacingLG)
        }
    }
    
    private var contentView: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            titleSection
            if mode == .apModeConfirmation {
                apModeConnectionView
            } else if mode == .apModeOnly {
                apModeOnlyView
            } else {
                optionSelectionView
            }
        }
    }
    
    private var titleSection: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(mode == .apModeConfirmation ? lang.apModeConfirmationTitle : lang.title(mode == .apModeOnly))
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .multilineTextAlignment(.leading)
                .lineLimit(nil)
            
            if mode == .apModeOnly {
                Text(lang.subtitle.asAttributed(withBoldWords: lang.boldWords))
                    .foregroundColor(theme.textBody)
            }
        }
    }
    
    private var apModeOnlyView: some View {
        VStack(alignment: .center) {
            apModeImage
                .padding(.vertical, .spacingLG)
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }
    
    private var apModeConnectionView: some View {
        VStack(alignment: .center) {
            if sku != sku0384 {
                Image(appAssets.stepOnImageName(sku: sku0396,
                                                isFilled: true,
                                                isDarkMode: themeManager.isDarkMode))
                .resizable()
                .frame(width: imageSize100, height: imageSize100)
            } else {
                GifView(
                    gifName: appAssets.wifiSetupStepOnGifName(
                        isFilled: true,
                        isDarkMode: themeManager.isDarkMode
                    ),
                    width: imageSize120,
                    height: imageSize100
                )
                .frame(width: imageSize120, height: imageSize100)
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }
    
    private var optionSelectionView: some View {
        HStack(spacing: .spacingMD) {
            setupCompleteOption
            apModeOption
        }
        .padding(.horizontal, .spacingXL)
        .padding(.top, .spacingSM)
        .frame(maxWidth: .infinity, alignment: .center)
    }
    
    private var setupCompleteOption: some View {
        VStack {
            Button(action: handleCompleteSelection) {
                if sku != sku0384 {
                    Image(appAssets.completeImageName(isFilled: selectedOption == .complete, isDarkMode: themeManager.isDarkMode))
                        .resizable()
                        .frame(width: imageSize100, height: imageSize100)
                } else {
                    if let userNumber = self.userNumber {
                        GifView(
                            gifName: appAssets.wifiSetupCompleteGifName(
                                user: userNumber,
                                isFilled: selectedOption == .complete,
                                isDarkMode: themeManager.isDarkMode
                            ),
                            width: imageSize120,
                            height: imageSize100
                        )
                        .frame(width: imageSize120, height: imageSize100)
                    }
                }
            }
        }
    }
    
    private var apModeOption: some View {
        VStack {
            Button(action: handleApModeSelection) {
                apModeImage
            }
        }
    }
    
    private var apModeImage: some View {
        let displaySku = sku == sku0384 ? sku0384 : sku0396
        let shouldUseFilled = mode == .apModeOnly ? true : (selectedOption == .apMode)
        return Image(
            appAssets.apModeImageName(
                sku: displaySku,
                isFilled: shouldUseFilled,
                isDarkMode: themeManager.isDarkMode
            )
        )
        .resizable()
        .frame(
            width: displaySku == sku0384 ? imageSize120 : imageSize100,
            height: imageSize100
        )
    }
    
    private var noteSection: some View {
        NoteBox {
            Text("**NOTE:** \(lang.note)")
                .fontOpenSans(.body3)
                .foregroundColor(theme.textBody)
        }
    }
    
    private var buttonSection: some View {
        ButtonView(
            text: wifiSetuplang.seeSomethingElse,
            type: .inlineTextPrimary,
            size: .large,
            isDisabled: false
        ) { onClickButton?() }
    }
    
    private func handleCompleteSelection() {
        onOptionSelected?(.complete)
    }
    
    private func handleApModeSelection() {
        onOptionSelected?(.apMode)
    }
}
