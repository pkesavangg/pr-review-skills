import Combine
import SwiftUI

/// A form with a publisher that emits before the form has changed.
///
/// The form collects all controls from its properties
/// that are marked as ``FormControl``.
///
///     class ProfileForm: ObservableForm {
///       var name = FormControl("", validators: [.required])
///       var email = FormControl("", validators: [.email])
///     }
open class ObservableForm: AbstractForm {
    /// Stores subscribers of `objectWillChange` from controls.
    private var cancellables: Set<AnyCancellable> = []
    private var controls: [ValidatableControl] = []
    
    /// Form-level validation errors
    @Published public private(set) var formErrors = ValidationErrors<Any>()
    
    /// A Boolean value indicating whether the form is valid.
    public var isValid: Bool {
        controls.allSatisfy { $0.isValid } && !formErrors.hasError
    }
    
    /// A Boolean value indicating whether the form is invalid.
    public var isInvalid: Bool {
        !isValid
    }
    
    /// A Boolean value indicating whether the form has not been changed yet.
    /// All of its controls are ``FormControl/isPristine``
    /// when the value is true.
    public var isPristine: Bool {
        !isDirty
    }
    
    /// A Boolean value indicating whether the form has been changed.
    /// Some of its controls are ``FormControl/isDirty``
    /// when the value is true.
    public var isDirty: Bool {
        controls.contains {
            $0.isDirty
        }
    }
    
    /// Creates a observable form and sets to initial state.
    public init() {
        collectControls(self)
        forwardObjectWillChangeFromControls()
        setupFormValidation()
    }
    
    /// Updates the validity of all controls in the form
    /// and also updates the validity of the form.
    public func validate() {
        controls.forEach {
            $0.validate()
        }
        validateForm()
    }
    
    /// Override this method to add form-level validation
    open func validateForm() {
        // Override in subclass to add form-level validation
    }
    
    /// Updates form-level validation errors
    public func updateFormErrors(_ errors: ValidationErrors<Any>) {
        formErrors = errors
        objectWillChange.send()
    }
    
    private func setupFormValidation() {
        // Watch for changes in controls and trigger form validation
        controls.forEach { control in
            if let formControl = control as? (any AbstractControl) {
                formControl.objectWillChange
                    .sink { [weak self] _ in
                        self?.validateForm()
                    }
                    .store(in: &cancellables)
            }
        }
    }
}

private extension ObservableForm {
    func collectControls(_ object: Any) {
        Mirror(reflecting: object)
            .children
            .forEach(collectControlIfPossible)
    }
    
    func collectControlIfPossible(child: Mirror.Child) {
        guard let control = child.value as? ValidatableControl else {
            // Properties annotated by `FormField`
            collectControls(child.value)
            return
        }
        
        controls.append(control)
    }
    
    func forwardObjectWillChangeFromControls() {
        controls.forEach(forward(from:))
    }
    
    /// Forwards `objectWillChange` of FormControl
    /// due to the nested `ObservableObject`.
    func forward(from control: ValidatableControl) {
        control
            .objectWillChange
            .sink(receiveValue: objectWillChange.send)
            .store(in: &cancellables)
    }
} 