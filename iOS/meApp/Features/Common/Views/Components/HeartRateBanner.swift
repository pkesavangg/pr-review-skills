import SwiftUI

struct HeartRateBanner: View {
    @Environment(\.appTheme) private var theme
    let isHeartRateOn: Bool
    let onUpdate: () -> Void
    var showOnlyContent: Bool = false

    private var iconAndLabelColor: Color {
        isHeartRateOn ? theme.statusIconPrimary : theme.statusIconSecondary
    }
    private var commonLang: CommonStrings.Type { CommonStrings.self }

    private var content: some View {
        HStack(spacing: .spacingSM) {
            HStack(spacing: .spacingXS) {
                AppIconView(icon: AppAssets.heartIcon, size: IconSize(width: 20, height: 20))
                    .foregroundColor(iconAndLabelColor)
                Text(commonLang.heartRateLabel)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)
                Text(isHeartRateOn ? commonLang.on.uppercased() : commonLang.off.uppercased())
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)
            }
            Spacer()
            ButtonView(
                text: commonLang.update.uppercased(),
                type: .textPrimary,
                size: .small,
                isDisabled: false,
                action: onUpdate
            )
        }
    }

    var body: some View {
        if showOnlyContent {
            content
        } else {
            NoteBox { content }
        }
    }
}
