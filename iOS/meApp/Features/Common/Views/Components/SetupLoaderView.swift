//
//  SetupLoaderView.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import SwiftUI
import Combine

struct SetupLoaderView: View {
    let connectionState: ConnectionState
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel = SetupLoaderViewModel()

    var dotColor: Color {
        switch connectionState {
        case .loading: return theme.brandWgPrimary
        case .success: return theme.statusSuccess
        case .failure: return theme.statusError
        }
    }

    func shouldAnimateDot(at index: Int) -> Bool {
        connectionState == .loading
    }

    var body: some View {
        VStack(spacing: 15) {
            ForEach(0..<5, id: \.self) { index in
                if (connectionState == .success || connectionState == .failure), index == 2 {
                    AppIconView(
                        icon: connectionState == .success ? AppAssets.filledTickCircle : AppAssets.filledCloseCircle,
                        size: IconSize(width: 30, height: 30)
                    )
                    .foregroundColor(
                        connectionState == .success ? theme.statusSuccess : theme.statusError
                    )
                } else {
                    Circle()
                        .fill(dotColor)
                        .frame(width: 10, height: 10)
                        .scaleEffect(shouldAnimateDot(at: index) ? viewModel.dotScales[index] : 1.0)
                        .animation(
                            shouldAnimateDot(at: index) ?
                            .easeInOut(duration: 0.6)
                                .repeatForever(autoreverses: true)
                                .delay(Double(index) * 0.15)
                            : .default,
                            value: shouldAnimateDot(at: index) ? viewModel.dotScales[index] : 1.0
                        )
                }
            }
        }
        .onAppear {
            viewModel.connectionState = connectionState
            viewModel.startAnimation()
        }
    }
}

struct StatusIndicationLoaderView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            SetupLoaderView(connectionState: .loading)
                .previewDisplayName("Loading")

            SetupLoaderView(connectionState: .success)
                .previewDisplayName("Success")

            SetupLoaderView(connectionState: .failure)
                .previewDisplayName("Failure")
        }
    }
}
