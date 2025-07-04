import Foundation

/// Validation errors of a form.
public struct ValidationErrors<Value> {
    private var errors: [ValidatorType: (hasError: Bool, value: Any?)] = [:]
    
    var hasError: Bool {
        errors.contains { $0.value.hasError }
    }
    
    mutating func update(
        for validator: Validator<Value>,
        value: Bool
    ) {
        errors.updateValue((hasError: !value, value: validator.value), forKey: validator.type)
    }
    
    /// Gets whether there is an error with the provided validator type.
    public subscript(_ type: ValidatorType) -> Bool {
        errors[type]?.hasError ?? false
    }
    
    /// Gets the value associated with a validator type
    public func value(for type: ValidatorType) -> Any? {
        errors[type]?.value
    }
}

public enum ValidatorType: Hashable {
    case required
    case email
    case min
    case max
    case minLength
    case maxLength
    case requiredTrue
    case noWhiteSpace
    case futureDate
    case matches
    case phoneNumber
    case passwordMatch
    case weightEqual
    case passwordDifferent
    case url
    case minValue
    case maxValue
    case skuMatch
    
    public func hash(into hasher: inout Hasher) {
        hasher.combine(String(describing: self))
    }
}
