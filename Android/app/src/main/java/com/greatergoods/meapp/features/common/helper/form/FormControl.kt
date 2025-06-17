package com.greatergoods.meapp.features.common.helper.form

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

typealias Validator<T> = (T) -> ValidationError?
typealias AsyncValidator<T> = suspend (T) -> ValidationError?
data class ValidationError(
    val type: String,
    val message: String
)
class FormControl<T>(
    initialValue: T,
    private val validators: List<Validator<T>> = emptyList(),
    private val asyncValidators: List<AsyncValidator<T>> = emptyList(),
    private val scope: CoroutineScope,
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

    fun onValueChange(newValue: T) {
        _value.value = newValue
        _dirty.value = true
        validate()
    }

    fun onBlur() {
        _touched.value = true
        validate()
    }

    fun validate(): Boolean {
        validationJob?.cancel()
        // Sync validators
        for (validator in validators) {
            val err = validator(value)
            if (err != null) {
                _error.value = err
                _pending.value = false
                return false
            }
        }
        // Async validators
        if (asyncValidators.isNotEmpty()) {
            _pending.value = true
            validationJob =
                scope.launch {
                    for (validator in asyncValidators) {
                        val err = validator(value)
                        if (err != null) {
                            _error.value = err
                            _pending.value = false
                            return@launch
                        }
                    }
                    _error.value = null
                    _pending.value = false
                }
        } else {
            _error.value = null
            _pending.value = false
        }
        return true
    }

    fun forceShowError() {
        _touched.value = true
    }
}


class FormGroup<T : Any>(
    val controls: T,
    private val groupValidators: List<(T) -> String?> = emptyList(),
) {
    private val _groupError = mutableStateOf<String?>(null)
    val groupError: String? get() = _groupError.value

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

    fun forceShowAllErrors() {
        controls.toList().forEach { it.forceShowError() }
    }
}

// Extension for explicit control access
fun <T : Any> T.toList(): List<FormControl<*>> =
    javaClass.declaredFields.mapNotNull { field ->
        field.isAccessible = true
        field.get(this) as? FormControl<*>
    }
