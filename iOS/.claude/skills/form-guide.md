---
name: form-guide
description: Form validation patterns — ObservableForm subclasses, FormControl fields, validators, error display, and store integration. Use when adding forms, validating fields, or handling form submission errors.
---

# Form Validation Guide

This guide explains how form validation works in meApp iOS. Forms are **owned by stores**, validated reactively, and integrated into views via bindings.

---

## Quick Reference

```swift
// 1. Define form class
class LoginForm: ObservableForm {
    @Published var email = FormControl(value: "")
    @Published var password = FormControl(value: "")
    
    override func validateForm() {
        updateFormErrors(FormError(
            email: Validators.email.validate(email.value),
            password: Validators.required.validate(password.value)
        ))
    }
}

// 2. Store owns and manages form
@MainActor final class LoginStore: ObservableObject {
    @Published var loginForm = LoginForm()
    @Published var submitError: String?
    
    func setupFormObservers() {
        loginForm.$value.sink { [weak self] _ in
            self?.loginForm.validateForm()
        }.store(in: &cancellables)
    }
    
    var isFormValid: Bool {
        loginForm.errors == nil && !loginForm.value.isEmpty
    }
    
    func submit() async {
        loginForm.markAsTouched()
        loginForm.validateForm()
        
        guard isFormValid else { return }
        
        do {
            try await authService.login(email: loginForm.email.value, password: loginForm.password.value)
        } catch {
            submitError = "Login failed: \(error.localizedDescription)"
        }
    }
}

// 3. View binds to form and errors
LoginScreen(store: store)
    AppInputField(
        config: .init(label: "Email", errorMessage: store.loginForm.errors?.email),
        value: $store.loginForm.email.value,
        onEditingChanged: { _ in store.loginForm.email.markAsTouched() }
    )
    
    Button("Login") { Task { await store.submit() } }
        .disabled(!store.isFormValid)
```

---

## Key Types

| Type | Purpose |
|------|---------|
| `ObservableForm` | Base class for all forms; manages `@Published errors` and validates on change |
| `FormControl<Value>` | Wraps a single field; tracks `value`, `isTouched`, `isDirty` |
| `Validator<Value>` | Single validation rule; returns error message or nil |
| `ValidatorType` | Enum of common validators (email, required, minLength, password, etc.) |
| `FormError` | Type-erased protocol for form error collections |

---

## Form Definition Pattern

### Define a Form Subclass

```swift
// Example: EntryForm for weight entry with metrics
class EntryForm: ObservableForm {
    @Published var weight = FormControl(value: "")
    @Published var unit = FormControl(value: "lbs")
    @Published var date = FormControl(value: Date())
    @Published var bodyfat = FormControl(value: "")  // Optional metric
    
    override func validateForm() {
        updateFormErrors(FormError(
            weight: Validators.required.validate(weight.value) ?? Validators.positiveNumber.validate(weight.value),
            unit: nil,  // No validation needed
            date: Validators.notFuture.validate(date.value),
            bodyfat: bodyfat.value.isEmpty ? nil : Validators.percentRange.validate(bodyfat.value)
        ))
    }
}
```

**Pattern:**
1. Subclass `ObservableForm`
2. Each field is `@Published var fieldName = FormControl(value: initialValue)`
3. Override `validateForm()` and call `updateFormErrors(FormError(...))`
4. Return `nil` for no error, error string if validation fails
5. Optional fields can be validated only if non-empty

### Supported Validators

```swift
// Required field
Validators.required.validate(value)  // → "Field is required" or nil

// Email format
Validators.email.validate(value)  // → "Invalid email" or nil

// Minimum length
Validators.minLength(8).validate(value)  // → "Min 8 characters" or nil

// Password strength (uppercase + lowercase + number + min 8)
Validators.password.validate(value)  // → "Invalid password" or nil

// Positive number
Validators.positiveNumber.validate(value)  // → "Must be positive" or nil

// Number range (0-100 for percentages)
Validators.percentRange.validate(value)  // → "Must be 0-100" or nil

// Date not in future
Validators.notFuture.validate(date)  // → "Cannot be in future" or nil

// Custom validator
Validators.custom({ value in
    value.count > 3 ? nil : "Too short"
}).validate(value)
```

---

## Cross-Field Validation

Validate relationships between fields (e.g., password != confirmPassword):

```swift
class ChangePasswordForm: ObservableForm {
    @Published var currentPassword = FormControl(value: "")
    @Published var newPassword = FormControl(value: "")
    @Published var confirmPassword = FormControl(value: "")
    
    override func validateForm() {
        var errors = ChangePasswordError()
        
        errors.currentPassword = Validators.required.validate(currentPassword.value)
        errors.newPassword = Validators.password.validate(newPassword.value)
        errors.confirmPassword = Validators.required.validate(confirmPassword.value)
        
        // Cross-field: newPassword != confirmPassword
        if newPassword.value != confirmPassword.value && !newPassword.value.isEmpty {
            errors.confirmPassword = "Passwords do not match"
        }
        
        // Cross-field: newPassword != currentPassword
        if newPassword.value == currentPassword.value && !newPassword.value.isEmpty {
            errors.newPassword = "New password must differ from current"
        }
        
        updateFormErrors(errors)
    }
}
```

---

## Store Ownership Pattern

Forms are **always owned by stores**. Stores manage submission, error display, and async side effects.

```swift
@MainActor final class ProfileStore: ObservableObject {
    @Published var profileForm = ProfileForm()
    @Published var isLoading = false
    @Published var submitError: String?
    
    private var cancellables = Set<AnyCancellable>()
    @Injector var accountService: AccountServiceProtocol
    
    init() {
        setupFormObservers()
    }
    
    // Subscribe to form changes and validate reactively
    func setupFormObservers() {
        profileForm.$value.sink { [weak self] _ in
            self?.profileForm.validateForm()
        }.store(in: &cancellables)
    }
    
    // Computed property for submit button disabled state
    var isSubmitDisabled: Bool {
        profileForm.errors != nil || isLoading
    }
    
    // Submit handler
    func updateProfile() async {
        profileForm.markAsTouched()
        profileForm.validateForm()
        
        guard profileForm.errors == nil else { return }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            try await accountService.updateProfile(
                name: profileForm.name.value,
                bio: profileForm.bio.value
            )
            submitError = nil
        } catch {
            submitError = error.localizedDescription
        }
    }
}
```

---

## View Integration Pattern

### Displaying Field Errors

```swift
struct ProfileScreen: View {
    @ObservedObject var store: ProfileStore
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        VStack(spacing: .spacingMD) {
            AppInputField(
                config: .init(
                    label: "Full Name",
                    errorMessage: store.profileForm.errors?.name,
                    placeholder: "John Doe"
                ),
                value: $store.profileForm.name.value,
                onEditingChanged: { _ in
                    store.profileForm.name.markAsTouched()
                }
            )
            .accessibilityIdentifier(AccessibilityID.profileNameField)
            
            AppInputField(
                config: .init(
                    label: "Bio",
                    errorMessage: store.profileForm.errors?.bio,
                    placeholder: "Tell us about yourself"
                ),
                value: $store.profileForm.bio.value,
                onEditingChanged: { _ in
                    store.profileForm.bio.markAsTouched()
                }
            )
            .accessibilityIdentifier(AccessibilityID.profileBioField)
            
            Button("Save Changes") {
                Task { await store.updateProfile() }
            }
            .disabled(store.isSubmitDisabled)
            .accessibilityIdentifier(AccessibilityID.profileSaveButton)
        }
        .padding(.spacingMD)
    }
}
```

**Pattern:**
- Bind field value via `$store.form.field.value`
- Display error via `store.form.errors?.field` (shows error string or nil)
- Call `markAsTouched()` on editing to reveal errors
- Disable submit button when `!isFormValid`

### Handling Submit Errors

```swift
VStack {
    // Form fields above...
    
    if let error = store.submitError {
        HStack(spacing: .spacingSM) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundColor(theme.statusError)
            Text(error)
                .font(.openSans(.body2))
                .foregroundColor(theme.statusError)
        }
        .padding(.spacingSM)
        .background(theme.statusErrorBackground)
        .cornerRadius(.radiusSM)
        .accessibilityIdentifier(AccessibilityID.submitError)
    }
}
```

---

## FormControl Advanced Features

### Tracking Field State

```swift
let emailControl = FormControl(value: "")

// Access field state
emailControl.value              // → Current input value
emailControl.isTouched          // → Has user interacted?
emailControl.isDirty            // → Has value changed from initial?

// Lifecycle
emailControl.markAsTouched()     // Call when user leaves field
emailControl.reset()              // Reset to initial value + untouched
```

### Optional Conditional Validation

```swift
class RegistrationForm: ObservableForm {
    @Published var email = FormControl(value: "")
    @Published var phone = FormControl(value: "")
    @Published var receiveTexts = FormControl(value: false)
    
    override func validateForm() {
        var errors = RegistrationError()
        
        errors.email = Validators.email.validate(email.value)
        
        // Phone is REQUIRED only if receiveTexts is true
        if receiveTexts.value {
            errors.phone = Validators.required.validate(phone.value)
        } else {
            errors.phone = nil  // Optional when unchecked
        }
        
        updateFormErrors(errors)
    }
}
```

---

## Observable Form Publisher Pattern

Subscribe to form value changes in stores for reactive updates:

```swift
// In store init
func setupFormObservers() {
    // Re-validate whenever ANY field changes
    loginForm.$value.sink { [weak self] _ in
        self?.loginForm.validateForm()
    }.store(in: &cancellables)
    
    // Example: Auto-fetch location suggestions on address change
    profileForm.$address.sink { [weak self] address in
        self?.onAddressChange(address.value)
    }.store(in: &cancellables)
}
```

---

## Golden Rules

### ✅ Do

- **Own forms in stores.** Forms are state; stores manage state.
- **Validate on change.** Call `validateForm()` whenever a field changes via `$value` subscription.
- **Mark touched on blur.** Call `markAsTouched()` in `onEditingChanged` to show errors only for interacted fields.
- **Disable submit when invalid.** Gate submit button with `!store.isFormValid`.
- **Use provided validators.** Don't reinvent email/password checks.

### ❌ Never

- **Validate in views.** Views should never call validation logic; only bind to errors.
- **Duplicate validators.** One rule = one validator instance shared by all forms.
- **Skip `markAsTouched()` on submit.** Call it first so cross-field errors are visible.
- **Mix form logic with business logic.** Keep forms pure validation; business logic stays in services.
- **Forget to `setupFormObservers()` in store init.** Without the subscription, form won't re-validate on change.

---

## File Structure

```
Features/{Feature}/
├── Forms/
│   ├── {Feature}Form.swift          # Form definition
│   └── {Feature}Error.swift         # FormError subtype
├── Stores/
│   └── {Feature}Store.swift         # @MainActor store with form ownership
└── Views/
    └── {Feature}Screen.swift        # View binds to store.form
```

---

## Related Skills

| Skill | Purpose |
|-------|---------|
| `/feature-slice` | Scaffolds feature module — includes Forms/ directory |
| `/gen-test-file` | Generate unit tests for forms and validators |
| `/add-strings` | Add form field labels and error messages |
| `/add-accessibility` | Add accessibility labels to input fields |

---

## Examples in Codebase

Search for existing form implementations:

```bash
rg "class.*Form.*:.*ObservableForm" meApp/Features --type swift
rg "FormControl" meApp/Features --type swift
rg "setupFormObservers" meApp/Features --type swift
```

Common forms:
- `LoginForm` (Auth feature)
- `EntryForm` (Entry feature — weight/metric entry)
- `RegistrationForm` (Auth feature — signup)
- `ChangePasswordForm` (Settings feature)
- `BabyScaleSetupForm` (ScaleSetup feature)

