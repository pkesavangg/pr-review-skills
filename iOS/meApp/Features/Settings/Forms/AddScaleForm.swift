//
//  AddScaleForm.swift
//  meApp
//
//  Created by Lakshmi Priya on 24/06/25.
//

import Foundation
import Combine

class AddScaleForm: ObservableObject {
    @Published var modelNumber = "" {
        didSet {
            let filtered = modelNumber.filter { $0.isNumber }
            guard filtered.count > 4 || filtered != modelNumber else { return }
            if filtered.count > 4 {
                modelNumber = String(filtered.prefix(4))
            } else {
                modelNumber = filtered
            }
            isDirty = true
        }
    }
    @Published var isDirty = false

    var isValid: Bool {
        modelNumber.count == 4 && Int(modelNumber) != nil
    }

    func getError(for field: FocusField) -> String? {
        guard isDirty else { return nil }
        switch field {
        case .modelNumber:
            if modelNumber.isEmpty || modelNumber.count < 4 {
                return "Model Number Invalid"
            }
        default:
            break
        }
        return nil
    }

    func reset() {
        modelNumber = ""
        isDirty = false
    }

    func setModelNumber(_ newValue: String) {
        let filtered = newValue.filter { $0.isNumber }
        let truncated = String(filtered.prefix(4))
        if truncated != modelNumber {
            modelNumber = truncated
        }
        isDirty = true
    }
}
