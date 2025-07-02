import SwiftUI

/// AppSync setup step instructing the user to step on the scale and capture the barcode.
struct TimeToWeighView: View {
    private let lang = AppSyncStrings.WeighInTimeStrings.self

    var body: some View {
        ScaleInstructionView(title: lang.title, description: lang.description)
    }
}

#Preview {
    TimeToWeighView()
        .environmentObject(Theme.shared)
} 