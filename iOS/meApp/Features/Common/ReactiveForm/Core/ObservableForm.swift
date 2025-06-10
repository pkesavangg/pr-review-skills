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



// MARK: Example Usage

import SwiftUI
import Combine

// Add Profile Data Model
struct ProfileData {
    let name: String
    let email: String
    let age: Int
    let rememberMe: Bool
    let username: String
    let dob: Date
    let password: String
    let confirmPassword: String
}

/// Constants for form validation error messages
enum SampleFormErrorMessages {
    static let required = "This field is required."
    static let email = "Please enter a valid email address."
    static let minLength = { (length: Int) in "Minimum \(length) characters required." }
    static let maxLength = { (length: Int) in "Maximum \(length) characters allowed." }
    static let min = { (value: Int) in "Value must be at least \(value)." }
    static let max = { (value: Int) in "Value must not exceed \(value)." }
    static let noWhiteSpace = "Field cannot contain only whitespace."
    static let futureDate = "Date cannot be in the future."
    static let requiredTrue = "This checkbox must be checked."
    static let passwordMatch = "Passwords do not match."
    static let url = "Please enter a valid URL."
} 

class BasicProfileForm: ObservableForm {
    var name = FormControl("", validators: [.required, .maxLength(10)])
    var email = FormControl("", validators: [.required, .email, .maxLength(100)])
    var age = FormControl(5, validators: [.min(10)])
    var rememberMe = FormControl(false, validators: [.requiredTrue])
    var username = FormControl("", validators: [.noWhiteSpace])
    var dob = FormControl(Date(), validators: [.futureDate])
    var password = FormControl("", validators: [.required, .minLength(6)])
    var confirmPassword = FormControl("", validators: [.required])
    var website = FormControl("", validators: [.required, .url])
    var weight = FormControl("", validators: [.required])
    
    override func validateForm() {
        var errors = ValidationErrors<Any>()
        
        // Check if passwords match when both are filled
        if !password.errors[.required] && !confirmPassword.errors[.required] {
            if password.value != confirmPassword.value {
                errors.update(
                    for: Validator<Any>(type: .passwordMatch) { _ in false },
                    value: false
                )
            }
        }
        
        updateFormErrors(errors)
    }
    
    // Function to update form values
    func updateFormValues(with profile: ProfileData) {
        name.value = profile.name
        email.value = profile.email
        age.value = profile.age
        rememberMe.value = profile.rememberMe
        username.value = profile.username
        dob.value = profile.dob
        password.value = profile.password
        confirmPassword.value = profile.confirmPassword
        
        // Validate all fields after update
        validate()
    }
    
    
    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }
        
        if control.errors[.required] { return SampleFormErrorMessages.required }
        if control.errors[.email] { return SampleFormErrorMessages.email }
        if control.errors[.minLength], let minLength = control.errors.value(for: .minLength) as? Int {
            return SampleFormErrorMessages.minLength(minLength)
        }
        if control.errors[.maxLength], let maxLength = control.errors.value(for: .maxLength) as? Int {
            return SampleFormErrorMessages.maxLength(maxLength)
        }
        if control.errors[.min], let minValue = control.errors.value(for: .min) as? Int {
            return SampleFormErrorMessages.min(minValue)
        }
        if control.errors[.noWhiteSpace] { return SampleFormErrorMessages.noWhiteSpace }
        if control.errors[.futureDate] { return SampleFormErrorMessages.futureDate }
        if control.errors[.requiredTrue] { return SampleFormErrorMessages.requiredTrue }
        if control === confirmPassword && formErrors[.passwordMatch] {
            return SampleFormErrorMessages.passwordMatch
        }
        if control.errors[.url] { return SampleFormErrorMessages.url }
        
        return nil
    }
}

struct BasicFormControlView: View {
    @StateObject var form = BasicProfileForm()
    
    @State var weight = ""
    
    var body: some View {
        VStack {
            
            // Bank input examples
            MetricInputField(
                config: TextInputConfig(
                    label: "weight (lbs)",
                    placeholder: "0.0",
                    inputType: .metric,
                    maxLength: 4,
                    maxValue: 999.9
                ),
                value: $weight,
                isFocused: .constant(false)
            )
            
            TextField("Name", text: $form.name.value)
            if let error = form.getError(for: form.name) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("Url", text: $form.website.value)
            if let error = form.getError(for: form.website) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("Email", text: $form.email.value)
            if let error = form.getError(for: form.email) {
                Text(error).foregroundColor(.red)
            }
            
            Stepper("Age: \(form.age.value)", value: $form.age.value, in: 0...100)
            if let error = form.getError(for: form.age) {
                Text(error).foregroundColor(.red)
            }
            
            Toggle("Remember Me", isOn: $form.rememberMe.value)
            if let error = form.getError(for: form.rememberMe) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("User Name", text: $form.username.value)
            if let error = form.getError(for: form.username) {
                Text(error).foregroundColor(.red)
            }
            
            DatePicker("Date of Birth", selection: $form.dob.value, displayedComponents: .date)
            if let error = form.getError(for: form.dob) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("Password", text: $form.password.value)
            if let error = form.getError(for: form.password) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("Confirm Password", text: $form.confirmPassword.value)
            if let error = form.getError(for: form.confirmPassword) {
                Text(error).foregroundColor(.red)
            }
            
            Button("mark as pristine") {
                form.name.markAsPristine()
            }
            
            Button("Submit") {
                if form.isValid {
                    print("Form submitted with:")
                    print("Name:", form.name.value)
                    print("Email:", form.email.value)
                }
            }
            .disabled(!form.isValid)
            
            Button("Load Sample Data") {
                let sampleProfile = ProfileData(
                    name: "John Doe",
                    email: "john.doe@example.com",
                    age: 25,
                    rememberMe: true,
                    username: "johndoe",
                    dob: Calendar.current.date(byAdding: .year, value: -25, to: Date()) ?? Date(),
                    password: "password123",
                    confirmPassword: "password123"
                )
                form.updateFormValues(with: sampleProfile)
            }
        }
        .navigationTitle("Basic Form Control")
    }
}

#Preview {
    BasicFormControlView()
}

