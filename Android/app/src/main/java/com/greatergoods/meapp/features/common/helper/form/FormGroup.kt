package com.greatergoods.meapp.features.common.helper.form

import androidx.compose.runtime.mutableStateOf

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
