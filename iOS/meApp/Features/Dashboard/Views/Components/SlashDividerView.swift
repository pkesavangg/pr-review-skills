import SwiftUI

struct SlashDividerView: View {
    let color: Color
    var body: some View {
        RoundedRectangle(cornerRadius: 0.5)
            .fill(color)
            .frame(width: 1, height: 47)
            .rotationEffect(.degrees(15))
    }
}
