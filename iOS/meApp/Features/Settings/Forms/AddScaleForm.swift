import Foundation
import Combine

class AddScaleForm: ObservableObject {
    @Published var modelNumber = "" {
        didSet {
            // Always enforce 4-digit numeric restriction
            let filtered = modelNumber.filter { $0.isNumber }
            if filtered.count > 4 {
                modelNumber = String(filtered.prefix(4))
            } else if filtered != modelNumber {
                modelNumber = filtered
            }
        }
    }
    @Published var isTouched = false
    let lang = MyScaleStrings.self
    
    var formDidChange: AnyPublisher<Void, Never> {
        $modelNumber.map { _ in () }.eraseToAnyPublisher()
    }
    
    var isValid: Bool {
        modelNumber.count == 4 && Int(modelNumber) != nil
    }
    
    var error: String? {
        guard isTouched else { return nil }
        if modelNumber.isEmpty { return "Model Number Invalid" }
        if modelNumber.count != 4 || Int(modelNumber) == nil { return "Model Number Invalid" }
        return nil
    }
    
    func markAsDirty() {
        isTouched = true
        objectWillChange.send()
        if modelNumber.isEmpty {
            modelNumber = ""
        }
    }

    func setModelNumber(_ newValue: String) {
        // Only allow up to 4 digits, and filter out non-numeric input
        let filtered = newValue.filter { $0.isNumber }
        let truncated = String(filtered.prefix(4))
        if truncated != modelNumber {
            modelNumber = truncated
        }
    }
    
    var displayModelNumber: String {
        modelNumber
    }
}
