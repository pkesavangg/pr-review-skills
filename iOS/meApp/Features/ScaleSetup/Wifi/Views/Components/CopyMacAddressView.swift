//
//  CopyMacAddressView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/07/25.
//

import SwiftUI

struct CopyMacAddressView: View {
    @Environment(\.appTheme) private var theme
    @StateObject var viewModel = CopyMacAddressViewModel()
    let macAddress: String
    
    private let lang = WifiScaleSetupStrings.CopyMacAddressViewStrings.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                contentView
                noteSection
            }
            .padding(.top, .spacingLG)
        }
    }
    
    private var contentView: some View {
        VStack(alignment: .leading, spacing: .spacingLG) {
            Text(lang.title)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .multilineTextAlignment(.leading)
            
            VStack(spacing: .spacingSM) {
                ActionListItemView(config: ActionListItemConfig(
                    title: macAddress,
                    chevronType: .none,
                ))
                .padding(.horizontal, .spacingSM)
                .background(theme.backgroundPrimary)
                .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
                
                ButtonView(
                    text: lang.copyMacAddress,
                    type: .inlineTextPrimary,
                    size: .large,
                    isDisabled: false,
                    action: copyMacAddress
                )
                .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }
    
    private var noteSection: some View {
        Text(lang.note)
            .fontOpenSans(.body2)
            .foregroundColor(theme.textBody)
    }
    
    private func copyMacAddress() {
        viewModel.copyMacAddress(macAddress: macAddress)
    }
}

#Preview {
    CopyMacAddressView(macAddress: "##:##:##:##:##:##")
        .environmentObject(Theme.shared)
}
