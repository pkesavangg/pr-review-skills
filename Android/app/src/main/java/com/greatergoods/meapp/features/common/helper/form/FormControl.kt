package com.greatergoods.meapp.features.common.helper.form

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias Validator<T> = (T) -> ValidationError?
typealias AsyncValidator<T> = suspend (T) -> ValidationError?

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
) {
    private val _value = mutableStateOf(initialValue)
    val value: T get() = _value.value

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

    /**
     * Updates the value and triggers validation
     */
    fun onValueChange(newValue: T) {
        _value.value = newValue
        _dirty.value = true
        validate()
    }

    /**
     * Marks the control as touched and triggers validation
     */
    fun onBlur() {
        _touched.value = true
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
     * Returns true if the current value passes all sync validators (regardless of touched/dirty state).
     */
    fun isValueValid(): Boolean {
        for (validator in _validators.value) {
            if (validator(value) != null) return false
        }
        return true
    }

    companion object {
        /**
         * Creates a new FormControl instance
         */
        fun <T> create(
            initialValue: T,
            validators: List<Validator<T>> = emptyList(),
        ): FormControl<T> =
            FormControl(
                initialValue = initialValue,
                validators = validators,
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
    private val _groupError = mutableStateOf<String?>(null)
    val groupError: String? get() = _groupError.value

    /**
     * Returns true if all controls and group-level validation are currently valid.
     * This checks all sync validators for each control, even if untouched.
     */
    val isValid: Boolean
        get() = controls.toList().all { it.isValueValid() } && groupError == null

    /**
     * Validates all controls in the group and runs group-level validation
     */
    fun validate(): Boolean {
        val fieldsValid = controls.toList().all { it.validate() && it.error == null }

        for (validator in groupValidators) {
            val err = validator(controls)
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
}

// Extension for explicit control access
private fun <T : Any> T.toList(): List<FormControl<*>> =
    javaClass.declaredFields.mapNotNull { field ->
        field.isAccessible = true
        field.get(this) as? FormControl<*>
    }
