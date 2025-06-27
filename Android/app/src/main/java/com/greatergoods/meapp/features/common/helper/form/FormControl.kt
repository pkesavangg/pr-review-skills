package com.greatergoods.meapp.features.common.helper.form

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.HeightInput
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
) {
    private val _value = mutableStateOf(initialValue)
    val value: T get() = _value.value
    private var _initialValue = initialValue

    private val _error = mutableStateOf<ValidationError?>(null)
    val error: ValidationError? get() = _error.value
    val errorMessage: String? get() = _error.value?.message
    val isError: Boolean get() = _error.value != null

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

    private var onValueChangeCallback: OnValueChangeCallback<T>? = null

    fun onValueChangeListener(callback: OnValueChangeCallback<T>) {
        onValueChangeCallback = callback
    }

    fun onValueChange(newValue: T) {
        val oldValue = _value.value
        _value.value = newValue
        _dirty.value = true
        onValueChangeCallback?.invoke(oldValue, newValue)
        validate()
    }

    fun onBlur() {
        _touched.value = true
        validate()
    }

    fun addValidator(validator: Validator<T>) {
        _validators.value = _validators.value + validator
        validate()
    }

    fun addAsyncValidator(type: String, validator: AsyncValidator<T>, scope: CoroutineScope) {
        _asyncValidators.value = _asyncValidators.value + AsyncValidatorWrapper(type, validator, scope)
        validate()
    }

    fun removeValidator(type: String) {
        _validators.value = _validators.value.filter { it(value)?.type != type }
        _asyncValidators.value = _asyncValidators.value.filter { it.type != type }
        validate()
    }

    fun validate(): Boolean {
        validationJob?.cancel()

        // Clear error before validation
        _error.value = null

        // Run sync validators
        for (validator in _validators.value) {
            val err = validator(value)
            if (err != null) {
                _error.value = err
                _pending.value = false
                return false
            }
        }

        // Run async validators
        if (_asyncValidators.value.isNotEmpty()) {
            _pending.value = true
            var pendingCount = _asyncValidators.value.size

            _asyncValidators.value.forEach { wrapper ->
                validationJob = wrapper.scope.launch {
                    validationMutex.withLock {
                        val err = wrapper.validator(value)
                        pendingCount--

                        if (err != null) {
                            _error.value = err
                            _pending.value = false
                            return@withLock
                        }

                        // Only set pending to false when all async validators are done
                        if (pendingCount == 0) {
                            _pending.value = false
                        }
                    }
                }
            }
        } else {
            _pending.value = false
        }

        return _error.value == null
    }

    fun forceShowError() {
        _touched.value = true
    }

    fun isValueValid(): Boolean {
        return _validators.value.all { it(value) == null }
    }

    fun reset(newInitialValue: T? = null) {
        val valueToReset = newInitialValue ?: _initialValue
        _initialValue = valueToReset
        _value.value = valueToReset
        _error.value = null
        _touched.value = false
        _dirty.value = false
        _pending.value = false
        validationJob?.cancel()
    }

    companion object {
        fun <T> create(initialValue: T, validators: List<Validator<T>> = emptyList()): FormControl<T> {
            return FormControl(initialValue, validators)
        }
    }

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
    private val json = Json { ignoreUnknownKeys = true }
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
    private inline fun <reified R : Any> getValuesAsType(): R {
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
        get() = controls.toFormControlList().all { it.isValueValid() } && groupError == null

    val isDirty: Boolean
        get() = controls.toFormControlList().any { it.dirty }

    val isTouched: Boolean
        get() = controls.toFormControlList().any { it.touched }

    val isPending: Boolean
        get() = controls.toFormControlList().any { it.pending }

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
        val controlList = controls.toFormControlList()
        val fieldsValid = controlList.all { it.validate() } && controlList.all { it.error == null }

        // Clear group error before validation
        _groupError.value = null

        for (validator in groupValidators) {
            val err = validator(controls)
            if (err != null) {
                _groupError.value = err
                return false
            }
        }

        return fieldsValid
    }

    /**
     * Forces error display for all controls in the group
     */
    fun forceShowAllErrors() {
        controls.toFormControlList().forEach { it.forceShowError() }
    }

    /**
     * Resets all controls in the group to their initial state
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
class MultiFormGroup(
    vararg formGroups: FormGroup<*>,
    private val crossValidators: List<() -> String?> = emptyList()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val groups = formGroups.toList()
    private val _groupError = mutableStateOf<String?>(null)
    val groupError: String? get() = _groupError.value

    val isValid: Boolean
        get() = groups.all { it.isValid } && groupError == null

    val isDirty: Boolean
        get() = groups.any { it.isDirty }

    val isTouched: Boolean
        get() = groups.any { it.isTouched }

    val isPending: Boolean
        get() = groups.any { it.isPending }

    /**
     * Validates all inner groups and cross-form validation rules.
     * Sets groupError if any cross-validator fails.
     */
    fun validate(): Boolean {
        val groupsValid = groups.all { it.validate() }

        // Clear group error before validation
        _groupError.value = null

        for (validator in crossValidators) {
            val err = validator()
            if (err != null) {
                _groupError.value = err
                return false
            }
        }

        return groupsValid
    }

    /**
     * Forces all controls in all groups to show their error states.
     */
    fun forceShowAllErrors() {
        groups.forEach { it.forceShowAllErrors() }
    }

    /**
     * Resets all controls in all groups to their initial state.
     */
    fun resetForm() {
        groups.forEach { it.resetForm() }
        _groupError.value = null
    }

    /**
     * Combines all field values from all inner groups into a single flat map.
     */
    fun getValues(): Map<String, Any?> {
        return groups.flatMap { it.getValues().entries }.associate { it.toPair() }
    }

    /**
     * Serializes combined values into the specified @Serializable type.
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
}

// Extension for explicit control access
private fun <T : Any> T.toFormControlList(): List<FormControl<*>> =
    javaClass.declaredFields.mapNotNull { field ->
        field.isAccessible = true
        field.get(this) as? FormControl<*>
    }
