//
//  WifiConnectionConfirmView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/07/25.
//

import SwiftUI

struct WifiConnectionConfirmView: View {
    @Environment(\.appTheme) private var theme
    let sku: String
    let userNumber: Int?
    let selectedOption: WifiSetupOption
    let isApModeAlone: Bool
    let onOptionSelected: ((WifiSetupOption) -> Void)?
    let onClickButton: (() -> Void)?
    
    private let wifiSetuplang = WifiScaleSetupStrings.self
    private let lang = WifiScaleSetupStrings.UserConfirmationViewStrings.self
    private let appAssets = AppAssets.self
    private let sku0384 = "0384"
    private let imageSize: CGFloat = 120
    
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
            if isApModeAlone {
                apModeOnlyView
            } else {
                optionSelectionView
            }
        }
    }
    
    private var titleSection: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(lang.title(isApModeAlone))
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .multilineTextAlignment(.leading)
                .lineLimit(nil)
            
            if isApModeAlone {
                Text(lang.subtitle.asAttributed(withBoldWords: lang.boldWords))
                    .foregroundColor(theme.textBody)
            }
        }
    }
    
    private var apModeOnlyView: some View {
        VStack(alignment: .center) {
            Button(action: handleApModeSelection) {
                apModeImage
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
                    Image(appAssets.wifiSetupComplete)
                        .resizable()
                        .frame(width: imageSize, height: imageSize)
                } else {
                    if let userNumber = self.userNumber {
                        GifView(
                            gifName: appAssets.wifiSetupCompleteGifName(user: userNumber),
                            width: imageSize,
                            height: imageSize
                        )
                        .frame(width: imageSize, height: imageSize)
                    }
                }
            }
            
            selectionIndicator(for: .complete)
        }
    }
    
    private var apModeOption: some View {
        VStack {
            Button(action: handleApModeSelection) {
                apModeImage
            }
            selectionIndicator(for: .apMode)
        }
    }
    
    private var apModeImage: some View {
        Image(appAssets.wifiApMode(sku))
            .resizable()
            .frame(width: imageSize, height: imageSize)
    }
    
    private func selectionIndicator(for option: WifiSetupOption) -> some View {
        AppIconView(
            icon: selectedOption == option ? AppAssets.circleCheckFilled : AppAssets.circleOutline,
            size: IconSize(width: 24, height: 24)
        )
        .foregroundColor(theme.statusIconPrimary)
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
            isDisabled: false,
            action: { onClickButton?() }
        )
    }
    
    private func handleCompleteSelection() {
        onOptionSelected?(.complete)
    }
    
    private func handleApModeSelection() {
        onOptionSelected?(.apMode)
    }
}
