import Combine

/// A form control with a publisher that emits before the control has changed.
public class FormControl<Value: Equatable>: AbstractControl {
    /// A value that stores a pending change of the control.
    /// Assigning to the value triggers validation and pristine check.
    @Published public var value: Value {
        willSet {
            objectWillChange.send()
        }
        didSet {
            if value != oldValue {
                markAsDirty()
                if validateType == .automatic {
                    validate()
                }
            }
        }
    }
    
    /// Validation type to the control.
    /// When value is `manually` you have to call `validate` manually
    public enum ValidateType {
        /// When value is changed it will automatic call the `validate()`
        case automatic
        /// You have to manually to call `validate()` otherwise isValid always be `true`
        case manually
    }
    
    private var validateType = ValidateType.automatic
    
    /// Validations applied to the control.
    public private(set) var validators: [Validator<Value>]
    
    /// Errors from the corresponding validators.
    @Published public private(set) var errors = ValidationErrors<Value>()
    
    /// A Boolean value indicating whether the control is valid.
    @Published public private(set) var isValid: Bool = true {
        didSet {
            isInvalid = !isValid
        }
    }
    
    /// A Boolean value indicating whether the control is invalid.
    @Published public private(set) var isInvalid: Bool = false
    
    /// A Boolean value indicating whether the ``value`` of the control has not been changed yet.
    @Published public private(set) var isPristine: Bool = true {
        didSet {
            isDirty = !isPristine
        }
    }
    
    /// A Boolean value indicating whether the ``value`` of the control has been changed.
    @Published public private(set) var isDirty: Bool = false
    
    /// A Boolean value indicating whether the control has been touched (focused).
    @Published public private(set) var isTouched: Bool = false
    
    /// Creates a form control with the provided value and its validators.
    public init(
        _ value: Value,
        validators: [Validator<Value>] = [],
        type: ValidateType = .automatic
    ) {
        self.value = value
        self.validators = validators
        self.validateType = type
        
        if validateType == .automatic {
            validate()
        }
    }
    
    /// Updates the value without triggering validation
    public func silentlyUpdateValue(_ newValue: Value) {
        if value != newValue {
            value = newValue
        }
    }
    
    /// Recalculates the validity of the control.
    public func validate() {
        collectErrors()
        setValidityByErrors()
    }
    
    /// Adds a validator to the control.
    /// - Parameter validator: The validator to add.
    public func addValidator(_ validator: Validator<Value>) {
        // Prevent adding duplicate validator types
        if !validators.contains(where: { $0.type == validator.type }) {
            validators.append(validator)
            if validateType == .automatic {
                validate()
            }
        }
    }
    
    /// Removes a validator of a specific type from the control.
    /// - Parameter type: The type of validator to remove.
    public func removeValidator(ofType type: ValidatorType) {
        let beforeCount = validators.count
        validators.removeAll { $0.type == type }
        if beforeCount != validators.count, validateType == .automatic {
            validate()
        }
    }
    
    private func setValidityByErrors() {
        isValid = !errors.hasError
    }
    
    private func collectErrors() {
        validators.forEach {
            errors.update(for: $0, value: $0.fn(value))
        }
    }
    
    /// Marks the control as pristine and also recalculates pristine state of its parent.
    public func markAsPristine() {
        isPristine = true
    }
    
    /// Marks the control as dirty and also recalculates pristine state of its parent.
    public func markAsDirty() {
        isPristine = false
    }
    
    /// Marks the control as touched (focused).
    public func markAsTouched() {
        isTouched = true
    }
    
    /// Marks the control as untouched (not yet focused).
    /// Use this when resetting a form field to its initial interaction state.
    public func markAsUntouched() {
        isTouched = false
    }
    
    /// Resets the control to its initial interaction state.
    /// Marks as pristine (not dirty), untouched, and re-validates.
    /// - Note: This does not reset the value; use `value = defaultValue` separately if needed.
    public func resetInteractionState() {
        markAsPristine()
        markAsUntouched()
        validate()
    }
} 