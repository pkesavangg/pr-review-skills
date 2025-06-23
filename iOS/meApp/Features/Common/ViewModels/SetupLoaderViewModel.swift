//
//  SetupLoaderViewModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import SwiftUI
import Combine

class SetupLoaderViewModel: ObservableObject {
    @Published var dotScales = Array(repeating: 0.5, count: 5)
    private var timer: Timer?
    private var isAnimating = false

    func startAnimation(when state: ConnectionState) {
        guard state == .loading, !isAnimating else { return }
        isAnimating = true

        timer = Timer.scheduledTimer(withTimeInterval: 0.6, repeats: true) { [weak self] timer in
            guard let self = self else { return }

            if state != .loading {
                timer.invalidate()
                self.isAnimating = false
                return
            }

            withAnimation {
                for i in 0..<self.dotScales.count {
                    self.dotScales[i] = self.dotScales[i] == 1.2 ? 0.8 : 1.2
                }
            }
        }
    }

    func stopAnimation() {
        timer?.invalidate()
        timer = nil
        isAnimating = false
    }
}
