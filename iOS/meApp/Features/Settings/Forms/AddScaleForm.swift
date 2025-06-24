import Foundation
import Combine

/// Manages adding a scale by model number with input sanitization and validation.
class AddScaleForm: ObservableObject {
    @Published var modelNumber = "" {
        didSet {
            sanitizeModelNumber()
        }
    }
    
    @Published var isDirty = false

    var isValid: Bool {
        modelNumber.count == 4 && Int(modelNumber) != nil
    }

    func getError(for field: FocusField) -> String? {
        guard isDirty else { return nil }
        if field == .modelNumber, modelNumber.count < 4 {
            return FormErrorMessages.modelNumberInvalid
        }
        return nil
    }

    func reset() {
        modelNumber = ""
        isDirty = false
    }

    func setModelNumber(_ newValue: String) {
        modelNumber = String(newValue.filter(\.isNumber).prefix(4))
        isDirty = true
    }

    private func sanitizeModelNumber() {
        let filtered = modelNumber.filter(\.isNumber).prefix(4)
        if modelNumber != filtered {
            modelNumber = String(filtered)
        }
        isDirty = true
    }
}
