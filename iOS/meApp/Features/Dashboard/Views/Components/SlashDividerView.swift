import SwiftUI

struct SlashDividerView: View {
    let color: Color
    var height: CGFloat = 47

    var body: some View {
        RoundedRectangle(cornerRadius: 0.5)
            .fill(color)
            .frame(width: 1, height: height)
            .rotationEffect(.degrees(15))
    }
}
