//
//  SetupLoaderViewModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import Combine
import SwiftUI

class SetupLoaderViewModel: ObservableObject {
    @Published var dotScales = Array(repeating: 0.5, count: 5)
    @Published var connectionState: ConnectionState = .loading
    private var timer: Timer?
    /// True while a repeating animation timer is scheduled. Exposed (setter stays
    /// private) so tests can assert the loading guard scheduled no tick, without a
    /// wall-clock wait.
    private(set) var isAnimating = false

    func startAnimation() {
        guard connectionState == .loading, !isAnimating else { return }
        isAnimating = true

        timer = Timer.scheduledTimer(withTimeInterval: 0.6, repeats: true) { [weak self] timer in
            guard let self = self else { return }

            if self.connectionState != .loading {
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
