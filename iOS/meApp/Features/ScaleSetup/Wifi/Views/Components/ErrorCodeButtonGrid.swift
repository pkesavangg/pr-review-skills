import SwiftUI

struct ErrorCodeButtonGrid: View {
    @Environment(\.appTheme) private var theme
    let errorCodes: [[String]]
    @Binding var selectedError: String?
    var onErrorSelected: ((String) -> Void)?
    private let buttonSize = CGFloat(100)
    private let lang = WifiScaleSetupStrings.ErrorCodeSelectionViewStrings.self
    var body: some View {
        VStack(spacing: .spacingMD) {
            ForEach(errorCodes, id: \.self) { row in
                HStack(spacing: .spacingMD) {
                    ForEach(row, id: \.self) { code in
                        Button {
                            selectedError = code
                            onErrorSelected?(code)
                        } label: {
                            ZStack {
                                Circle()
                                    .fill(selectedError == code ? theme.actionPrimary : Color.clear)
                                    .frame(width: buttonSize, height: buttonSize)
                                    .overlay(
                                        Circle()
                                            .stroke(theme.actionPrimary, lineWidth: 1)
                                    )
                                
                                VStack(spacing: 2) {
                                    Text(code)
                                        .fontOpenSans(.heading3)
                                        .foregroundColor(selectedError == code ? theme.actionInverse : theme.actionPrimary)
                                        .minimumScaleFactor(0.5)
                                    
                                    Text(lang.err)
                                        .fontOpenSans(.button1)
                                        .foregroundColor(selectedError == code ? theme.actionInverse : theme.actionPrimary)
                                        .minimumScaleFactor(0.5)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .aspectRatio(1, contentMode: .fit)
                    }
                }
            }
        }
        .padding(.horizontal, .spacingSM)
    }
}

#Preview {
    ErrorCodeButtonGrid(
        errorCodes: [
            ["t163", "t164", "t165"],
            ["t204", "t205", "t206"],
            ["t315", "t323", "t325"]
        ],
        selectedError: .constant("t164")
    ) { code in
        print("Selected error: \(code)")
    }
    .environmentObject(Theme.shared)
} 
