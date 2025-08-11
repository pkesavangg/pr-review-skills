import SwiftUI

struct ErrorCodeButtonGrid: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    let errorCodes: [[WifiErrorCode]]
    @Binding var selectedError: WifiErrorCode?
    var onErrorSelected: ((WifiErrorCode) -> Void)?
    let sku: String
    private let buttonSize = CGFloat(100)
    private let lang = WifiScaleSetupStrings.ErrorCodeSelectionViewStrings.self
    private let sku0384 = "0384"
    var body: some View {
        VStack(spacing: .spacingMD) {
            ForEach(errorCodes, id: \.self) { row in
                HStack(spacing: .spacingMD) {
                    ForEach(row, id: \.self) { code in
                        Button {
                            selectedError = code
                            onErrorSelected?(code)
                        } label: {
                            VStack(spacing: 4) {
                                // Error code image
                                Image(getImageName(for: code, isSelected: selectedError == code))
                                    .resizable()
                                    .aspectRatio(contentMode: .fit)
                                    .frame(width: buttonSize, height: sku == sku0384 ? buttonSize * 0.6 : buttonSize)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, .spacingXS)
                    }
                }
            }
        }
        .padding(.horizontal, .spacingSM)
    }
    
    /// Generates the appropriate image name based on error code, selection state, and theme
    private func getImageName(for errorCode: WifiErrorCode, isSelected: Bool) -> String {
        return AppAssets.errorCodeImageName(
            sku: sku,
            errorCode: errorCode.rawValue,
            isFilled: isSelected,
            isDarkMode: themeManager.isDarkMode
        )
    }
}

//#Preview {
//    ErrorCodeButtonGrid(
//        errorCodes: [
//            [.t163, .t164, .t165],
//            [.t204, .t205, .t206],
//            [.t315, .t323, .t325]
//        ],
//        selectedError: .constant(.t164),
//        sku: "0384"
//    ) { code in
//        print("Selected error: \(code)")
//    }
//    .environmentObject(Theme.shared)
//} 
