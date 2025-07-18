//
//  AllBodyMetricsContentView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/07/25.
//
import SwiftUI

/// Extracted content view for All Body Metrics
struct AllBodyMetricsContentView: View {
    @Environment(\.appTheme) private var theme
    @Binding var isHeartRateOn: Bool
    let onHeartRateChanged: (Bool) -> Void

    private let lang = ScaleModesStrings.self
    private let commonLang = CommonStrings.self

    var body: some View {
        let iconAndLabelColor = isHeartRateOn ? theme.statusIconPrimary : theme.statusIconSecondary

        VStack {
            VStack(spacing: 0) {
                HStack(alignment: .center, spacing: .spacingXS) {
                    StatusRowView(
                        iconName: AppAssets.heartIcon,
                        label: commonLang.heartRateLabel,
                        statusText: isHeartRateOn ? commonLang.on.uppercased() : commonLang.off.uppercased(),
                        foregroundColor: iconAndLabelColor
                    )
                    .fontWeight(.bold)

                    Spacer()
                    
                    CustomToggleView(isOn: Binding(
                        get: { isHeartRateOn },
                        set: { newValue in
                            isHeartRateOn = newValue
                            onHeartRateChanged(newValue)
                        }
                    ))
                    
                }

                Text(lang.heartRateInfoDescription)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)
                    .padding(.top, .spacingSM)
            }
            .padding(.bottom, .spacingMD)

            NoteBox {
                AttributedTextView(title: lang.noteTitle.uppercased(), content: lang.medicalNoteDescription)
            }
        }
        .background(theme.backgroundSecondary)

        
    }
}
