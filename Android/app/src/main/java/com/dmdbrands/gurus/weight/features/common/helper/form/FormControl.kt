package com.dmdbrands.gurus.weight.features.common.helper.form

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

typealias Validator<T> = (T) -> ValidationError?
typealias AsyncValidator<T> = suspend (T) -> ValidationError?
typealias OnValueChangeCallback<T> = (oldValue: T, newValue: T) -> Unit

data class ValidationError(
    val type: String,
    val message: String,
)

/**
 * A form control that manages the state and validation of a single form field.
 * @param T The type of value this control manages
 * @param initialValue The initial value of the control
 * @param validators List of synchronous validators
 */
@Stable
class FormControl<T> private constructor(
    initialValue: T,
    validators: List<Validator<T>> = emptyList(),
    onValueChangeCallback: OnValueChangeCallback<T>? = null
) {
    private val _value = mutableStateOf(initialValue)
    val value: T get() = _value.value
    private var _initialValue = initialValue

    private val _error = mutableStateOf<ValidationError?>(null)
    val error: ValidationError? get() = _error.value
    val errorMessage: String? get() = _error.value?.message
    val isError: Boolean get() = _error.value != null && _error.value?.type != null

    private val _touched = mutableStateOf(false)
    val touched: Boolean get() = _touched.value

    private val _dirty = mutableStateOf(false)
    val dirty: Boolean get() = _dirty.value

    private val _pending = mutableStateOf(false)
    val pending: Boolean get() = _pending.value

    private var validationJob: Job? = null
    private val validationMutex = Mutex()

    private val _validators = mutableStateOf(validators)
    private val _asyncValidators = mutableStateOf<List<AsyncValidatorWrapper<T>>>(emptyList())

    private var onValueChangeCallback: OnValueChangeCallback<T>? = onValueChangeCallback
    private var suppressNextBlurTouch: Boolean = false
    /**
     * Sets a callback to be notified when the value changes
     * @param callback The callback function that receives both old and new values
     */
    fun onValueChangeListener(callback: OnValueChangeCallback<T>) {
        onValueChangeCallback = callback
    }

    /**
     * Updates the value and triggers validation
     */
    fun onValueChange(newValue: T) {
        val oldValue = _value.value
        _value.value = newValue
        _dirty.value = true
        onValueChangeCallback?.invoke(oldValue, newValue)
        validate()
    }

    /**
     * Marks the control as touched and triggers validation
     */
    fun onBlur(isTouched: Boolean = true) {
      if (suppressNextBlurTouch) {
        // Consume the first blur after reset so the control stays untouched.
        suppressNextBlurTouch = false
        _touched.value = false
        _error.value = null
        _pending.value = false
        return
      }
      _touched.value = isTouched
        validate()
    }

    /**
     * Adds a synchronous validator to the control
     */
    fun addValidator(validator: Validator<T>) {
        _validators.value = _validators.value + validator
        validate()
    }

    /**
     * Adds an asynchronous validator to the control
     * @param type The type identifier for the async validator
     * @param validator The async validator function
     * @param scope The coroutine scope to run the validator in
     */
    fun addAsyncValidator(
        type: String,
        validator: AsyncValidator<T>,
        scope: CoroutineScope,
    ) {
        _asyncValidators.value = _asyncValidators.value + AsyncValidatorWrapper(type, validator, scope)
        validate()
    }

    /**
     * Removes a validator by type
     */
    fun removeValidator(type: String) {
        _validators.value =
            _validators.value.filter { validator ->
                validator(value)?.type != type
            }
        _asyncValidators.value =
            _asyncValidators.value.filter { wrapper ->
                wrapper.type != type
            }
        validate()
    }

    /**
     * Validates the control using both sync and async validators
     * @return true if validation passed synchronous checks
     */
    fun validate(): Boolean {
        validationJob?.cancel()
// ✅ 1. Do NOT validate if untouched AND not dirty
      if (!touched && !dirty) {
        _error.value = null
        _pending.value = false
        return true
      }
        // Run sync validators first
        for (validator in _validators.value) {
            val err = validator(value)
            if (err != null) {
                _error.value = err
                _pending.value = false
                return false
            }
        }

        // If we have async validators, run them
        if (_asyncValidators.value.isNotEmpty()) {
            _pending.value = true

            // Launch each async validator in its own scope
            _asyncValidators.value.forEach { wrapper ->
                wrapper.scope.launch {
                    validationMutex.withLock {
                        val err = wrapper.validator(value)
                        if (err != null) {
                            _error.value = err
                            _pending.value = false
                            return@launch
                        }
                        // Only clear error if no other async validators have set an error
                        if (_error.value == null) {
                            _pending.value = false
                        }
                    }
                }
            }
        } else {
            _error.value = null
            _pending.value = false
        }
        return true
    }

    /**
     * Forces error display regardless of touched/dirty state
     */
    fun forceShowError() {
        _touched.value = true
    }

    /**
     * Explicitly marks the control as touched
     */
    fun markAsTouched() {
        _touched.value = true
    }

    /**
     * Explicitly marks the control as dirty
     */
    fun markAsDirty() {
        _dirty.value = true
    }

    /**
     * Sets the value without triggering validation or callbacks (useful for programmatic updates)
     */
    fun setValue(newValue: T) {
        _value.value = newValue
        _dirty.value = true
    }

    /**
     * Returns true if the current value passes all sync validators (regardless of touched/dirty state).
     */
    fun isValueValid(): Boolean {
        for (validator in _validators.value) {
            if (validator(value) != null) return false
        }
        return true
    }

    /**
     * Resets the control to its initial state
     * @param newInitialValue Optional new initial value to set
     */
    fun reset(newInitialValue: T? = null) {
        val valueToReset = newInitialValue ?: _initialValue
        _initialValue = valueToReset
        _value.value = valueToReset
        _error.value = null
        _touched.value = false
        _dirty.value = false
        _pending.value = false
      suppressNextBlurTouch = true
        validationJob?.cancel()
    }

    companion object {
        /**
         * Creates a new FormControl instance
         */
        fun <T> create(
            initialValue: T,
            validators: List<Validator<T>> = emptyList(),
            onValueChangeCallback: OnValueChangeCallback<T>? = null,
        ): FormControl<T> =
            FormControl(
                initialValue = initialValue,
                validators = validators,
                onValueChangeCallback = onValueChangeCallback,
            )
    }

    /**
     * Wrapper class to keep async validator, its type, and its scope together
     */
    private data class AsyncValidatorWrapper<T>(
        val type: String,
        val validator: AsyncValidator<T>,
        val scope: CoroutineScope,
    )
}

/**
 * A group of form controls with optional group-level validation
 */
@Stable
class FormGroup<T : Any>(
    val controls: T,
    private val groupValidators: List<(T) -> String?> = emptyList(),
) {
    val json = Json { ignoreUnknownKeys = true }
    private val _groupError = mutableStateOf<String?>(null)
    val groupError: String? get() = _groupError.value

    /**
     * Serializes the form's values into a custom data type `R`.
     *
     * This method collects all values from the form controls, constructs a JSON object,
     * and then deserializes it into the specified type `R`. The target type `R` must
     * be annotated with `@Serializable`.
     *
     * Note: This works best with primitive types (String, Number, Boolean) and Enums.
     * For complex objects as form control values, ensure their `toString()` representation
     * is compatible with the expected JSON value.
     *
     * @param R The target `@Serializable` data class, which must be `reified`.
     * @return An instance of `R` populated with the form's values.
     */
    inline fun <reified R : Any> getValuesAsType(): R {
        val valueMap = getValues()
        val jsonObjectMap =
            valueMap.mapValues { (_, value) ->
                when (value) {
                    null -> JsonNull
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is DateTimeValue -> json.encodeToJsonElement(value)
                    is HeightInput -> json.encodeToJsonElement(value)
                    else -> JsonPrimitive(value.toString()) // Fallback for other types like enums
                }
            }
        return json.decodeFromJsonElement(JsonObject(jsonObjectMap))
    }

    /**
     * Returns true if all controls and group-level validation are currently valid.
     * This checks all sync validators for each control, even if untouched.
     */
    val isValid: Boolean
        get() = controls.toList().all { it.isValueValid() } && groupError == null

    val isDirty: Boolean
        get() = controls.toList().any { it.dirty }

    val isTouched: Boolean
        get() = controls.toList().any { it.touched }

    val isPending: Boolean
        get() = controls.toList().any { it.pending }

    /**
     * Returns a map of all form control values where the key is the field name
     * and the value is the current value of that form control
     */
    fun getValues(): Map<String, Any?> =
        controls.javaClass.declaredFields
            .mapNotNull { field ->
                field.isAccessible = true
                val control = field.get(controls) as? FormControl<*>
                if (control != null) {
                    field.name to control.value
                } else {
                    null
                }
            }.toMap()

    /**
     * Validates all controls in the group and runs group-level validation
     */
    fun validate(): Boolean {
        val fieldsValid = controls.toList().all { it.validate() && it.error == null }

        for (validator in groupValidators) {
            val err = validator(controls)
          AppLog.e("Form", "Group validator: $err")
            if (err != null) {
                _groupError.value = err
                return false
            }
        }
        _groupError.value = null
        return fieldsValid && groupError == null
    }

    /**
     * Forces error display for all controls in the group
     */
    fun forceShowAllErrors() {
        controls.toList().forEach { it.forceShowError() }
    }

    /**
     * Marks all controls in the group as touched
     */
    fun markAllAsTouched() {
        controls.toList().forEach { it.markAsTouched() }
    }

    /**
     * Marks all controls in the group as dirty
     */
    fun markAllAsDirty() {
        controls.toList().forEach { it.markAsDirty() }
    }

    /**
     * Resets all controls in the group to their initial state
     * @param newValues Optional map of field names to new initial values
     */
    fun resetForm() {
        controls.javaClass.declaredFields.forEach { field ->
            field.isAccessible = true
            val control = field.get(controls)
            if (control is FormControl<*>) {
                control.reset()
            }
        }
        _groupError.value = null
    }
}

@Stable
class MultiFormGroup<T : Any>(
    val forms: T,
    private val crossValidators: List<(T) -> String?> = emptyList()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _groupError = mutableStateOf<String?>(null)
    val groupError: String? get() = _groupError.value

    /**
     * Returns true if all nested FormGroups are valid and cross-validation passes
     */
    val isValid: Boolean
        get() = forms.toFormGroupList().all { it.isValid } && groupError == null

    val isDirty: Boolean
        get() = forms.toFormGroupList().any { it.isDirty }

    val isTouched: Boolean
        get() = forms.toFormGroupList().any { it.isTouched }

    val isPending: Boolean
        get() = forms.toFormGroupList().any { it.isPending }

    /**
     * Validates all nested FormGroups and runs cross-validation
     */
    fun validate(): Boolean {
        val formGroups = forms.toFormGroupList()
        val formsValid = formGroups.all { it.validate() }

        // Clear group error before validation
        _groupError.value = null

        for (validator in crossValidators) {
            val err = validator(forms)
            if (err != null) {
                _groupError.value = err
                return false
            }
        }

        return formsValid
    }

    /**
     * Forces all controls in all nested FormGroups to show their error states
     */
    fun forceShowAllErrors() {
        forms.toFormGroupList().forEach { it.forceShowAllErrors() }
    }

    /**
     * Marks all controls in all nested FormGroups as touched
     */
    fun markAllAsTouched() {
        forms.toFormGroupList().forEach { it.markAllAsTouched() }
    }

    /**
     * Marks all controls in all nested FormGroups as dirty
     */
    fun markAllAsDirty() {
        forms.toFormGroupList().forEach { it.markAllAsDirty() }
    }

    /**
     * Resets all controls in all nested FormGroups to their initial state
     */
    fun resetForm() {
        forms.toFormGroupList().forEach { it.resetForm() }
        _groupError.value = null
    }

    /**
     * Combines all field values from all nested FormGroups into a single flat map
     */
    fun getValues(): Map<String, Any?> {
        return forms.toFormGroupList().flatMap { it.getValues().entries }.associate { it.toPair() }
    }

    /**
     * Returns a nested map structure preserving the original form organization
     */
    fun getNestedValues(): Map<String, Map<String, Any?>> {
        return forms.javaClass.declaredFields
            .mapNotNull { field ->
                field.isAccessible = true
                val formGroup = field.get(forms) as? FormGroup<*>
                if (formGroup != null) {
                    field.name to formGroup.getValues()
                } else {
                    null
                }
            }.toMap()
    }

    /**
     * Serializes combined values into the specified @Serializable type
     */
    private inline fun <reified R : Any> getValuesAsType(): R {
        val valueMap = getValues()
        val jsonObjectMap = valueMap.mapValues { (_, value) ->
            when (value) {
                null -> JsonNull
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is DateTimeValue -> json.encodeToJsonElement(value)
                is HeightInput -> json.encodeToJsonElement(value)
                else -> JsonPrimitive(value.toString())
            }
        }
        return json.decodeFromJsonElement(JsonObject(jsonObjectMap))
    }

    companion object {
        fun <T : Any> create(forms: T, crossValidators: List<(T) -> String?> = emptyList()): MultiFormGroup<T> {
            return MultiFormGroup(forms, crossValidators)
        }
    }
}

// Extension for nested FormGroup access
private fun <T : Any> T.toFormGroupList(): List<FormGroup<*>> =
    javaClass.declaredFields.mapNotNull { field ->
        field.isAccessible = true
        field.get(this) as? FormGroup<*>
    }

// Extension for explicit control access
private fun <T : Any> T.toList(): List<FormControl<*>> =
    javaClass.declaredFields.mapNotNull { field ->
        field.isAccessible = true
        field.get(this) as? FormControl<*>
    }
