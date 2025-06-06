# Jetpack Compose Reactive Form Usage Guidelines

This document describes best practices and usage patterns for the `FormControl`/`FormGroup` pattern in Jetpack Compose,
inspired by Angular Reactive Forms. This approach ensures forms are robust, testable, and easy to maintain for any size
Compose app.

---

## 1. **Principles**

* **Centralized state:** All form fields and validation logic are stored in the ViewModel, not in composables.
* **Unidirectional data flow:** UI reacts to state from ViewModel; only ViewModel mutates state.
* **Separation of concerns:** Input composables handle only presentation and user interaction.
* **Validation-first:** Each field is validated automatically as values change.
* **Extensible:** Supports synchronous and asynchronous validation, cross-field (group) validation, and any input type.

---

## 2. **Basic Architecture**

### **2.1. Define Validators**

* Validators are lambdas returning `null` if valid, or an error message if invalid.
* Both synchronous and asynchronous (suspend) validators are supported.

```kotlin
typealias Validator<T> = (T) -> String?
typealias AsyncValidator<T> = suspend (T) -> String?
```

### **2.2. Create FormControls in ViewModel**

* Always instantiate `FormControl` objects in the ViewModel, not in composables.
* Pass the `viewModelScope` to each control for safe async validation and job management.

```kotlin
class MyFormViewModel : ViewModel() {
    val email = FormControl(
        initialValue = "",
        validators = listOf({ if (it.isBlank()) "Email required" else null }),
        scope = viewModelScope
    )
    // ... other controls
}
```

### **2.3. Bundle Controls in a Data Class and FormGroup**

* Define a data class to group your controls for type-safe access.
* Pass the controls to `FormGroup` along with any group (cross-field) validators.

```kotlin
data class MyFormControls(
    val email: FormControl<String>,
    val password: FormControl<String>
)

val controls = MyFormControls(email, password)
val form = FormGroup(
    controls = controls,
    groupValidators = listOf { c ->
        if (c.password.value.length < 8) "Password too short" else null
    }
)
```

### **2.4. Use FormControlInput Custom Composables**

* Each input field composable accepts a `FormControl` for automatic value, error, touched, and pending state management.

```kotlin
FormControlInput(
    label = "Email",
    formControl = controls.email
)
```

---

## 3. **Form Submission Flow**

* Always call `form.validate()` before submit.
* Only proceed if `form.groupError == null` and all controls have `error == null && !pending`.
* Optionally, show global errors or submit feedback in the UI.

```kotlin
if (form.validate() && form.groupError == null && controls.email.error == null) {
    // Submit form data
}
```

---

## 4. **Async Validation**

* Async validators run in ViewModel scope and are automatically canceled/restarted as users type.
* Use `pending` state to indicate ongoing validation in UI.

---

## 5. **Advanced Patterns**

* **Cross-field validation:** Add group validators for rules like password match.
* **Non-string fields:** Provide `valueToString` and `stringToValue` lambdas for numbers, enums, etc.
* **Custom widgets:** Extend `FormControlInput` for dropdowns, date pickers, checkboxes, etc.
* **Reusable utilities:** Extract common validators and input components to shared modules.

---

## 6. **Best Practices**

* Never instantiate controls in a composable—always in the ViewModel.
* Use `viewModelScope` for all async operations to avoid leaks.
* Avoid runtime reflection—define controls explicitly in a data class.
* Use composables only for display and interaction, not business logic.
* Use strong typing for safer code and easier refactoring.
* Test form logic in unit tests by instantiating controls and groups.

---

## 7. **Common Pitfalls**

* **Memory leaks:** Never pass a composable scope to FormControl; always use ViewModel scope.
* **State loss:** If you re-create controls every recomposition, user input and validation will be lost.
* **Async validator bugs:** Ensure jobs are canceled and never left running after ViewModel is destroyed.
* **Incorrect error display:** Always show error messages only if the field is touched and has an error.

---

## 8. **Example Project Structure**

* `form/`

    * `FormControl.kt`  // Contains FormControl and validators
    * `FormGroup.kt`    // Contains FormGroup and data classes for each form
    * `FormInputs.kt`   // Custom composables for text, password, dropdown, etc.
    * `validators/`     // Common validator functions
* `ui/`

    * `MyFormScreen.kt` // Actual Compose screen, binds controls to UI
    * `MyFormViewModel.kt` // ViewModel owning the form controls/group

---

## 9. **References & Further Reading**

* [Angular Reactive Forms Docs](https://angular.io/guide/reactive-forms)
* [Jetpack Compose State Documentation](https://developer.android.com/jetpack/compose/state)
* [Kotlin Coroutines for Android](https://developer.android.com/kotlin/coroutines)

---

**With this pattern, you can scale Compose forms to any complexity, maintain DRY validation, and offer a familiar,
maintainable form developer experience!**
