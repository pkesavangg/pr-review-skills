package com.greatergoods.meapp.features.common.helper.form

import androidx.compose.runtime.mutableStateOf
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


data class FormBuilder(val initialFormFields: Map<String, FormField<Any>>) { // Changed to val
    private val _form: MutableStateFlow<Form>

    init {
        // Create the initial Form instance.
        // The Form constructor will call assignForm to set parent references.
        // Initial validation states of fields will be their defaults (e.g., isValid = false).
        _form = MutableStateFlow(Form(initialFormFields) { updatedFieldMap ->
            updateField(updatedFieldMap)
        })
    }

    private fun updateField(validatedFieldsMap: Map<String, FormField<Any>>) {
        _form.update { /* previousForm -> */ // previousForm instance is not needed here
            // Create a new Form instance with the complete, validated map of fields.
            Form(
                fields = validatedFieldsMap,
                updateCallback = { nextValidatedFieldsMap ->
                    updateField(nextValidatedFieldsMap)
                }
            )
        }
    }

    val form: StateFlow<Form> = _form.asStateFlow()

    fun getAllValues(): Map<String, Any> {
        return form.value.getAllValues()
    }

    fun getForm(): Form {
        return form.value
    }

}

data class Form(
    val fields: Map<String, FormField<Any>>, // Changed to val
    val updateCallback: (result: Map<String, FormField<Any>>) -> Unit
) {

    val isFormValid: Boolean
        get() = isValidForm()
    val isFormTouched: Boolean
        get() = checkFormTouched()
    val isFormDirty: Boolean
        get() = fields.any { it.value.isDirty }

    init {
        assignForm()
        // Initial validation pass removed from here. Fields will start with their default
        // validation state (e.g. isValid=false from FormField default) and will be
        // validated upon interaction. If initial validation messages are required immediately,
        // FormBuilder would need to perform an initial validation pass and construct
        // the first Form instance with those pre-validated fields.
    }

    private fun assignForm() {
        fields.forEach {
            // It's important that FormField.parent is a var for this to work across Form instances.
            it.value.setParentValue(this)
        }
    }

    fun markAsPristine() {
        var newFields = fields
        fields.forEach { (key, field) ->
            val updatedField = field.copy(isTouched = false, isDirty = false)
            // Validate after marking pristine, as validation state might depend on isTouched
            // This part might need more thought if markAsPristine should also clear errors.
            // For now, just updating flags and re-validating.
            newFields = newFields + (key to updatedField) // create new map
        }
        // After updating all fields, perform validation on the new map
        var revalidatedFields = newFields
        newFields.keys.forEach { fieldName ->
            revalidatedFields = validateFieldInternal(fieldName, revalidatedFields, isPristineUpdate = true)
        }
        updateCallback(revalidatedFields)
    }

    fun update(fieldName: String, value: Any, checkValid: Boolean = true) {
        if (fields.containsKey(fieldName)) {
            Log.e("VALUE", value.toString())
            var updatedField = fields[fieldName]?.copy(value = value)
            if (checkValid) {
                // Mark as touched and dirty only if checkValid is true (i.e., user interaction)
                updatedField = updatedField?.copy(isTouched = true, isDirty = true)
            }
            // Create a temporary map with the single raw update
            val tempFieldsWithUpdate = fields + (fieldName to updatedField!!)

            // Validate the updated field and get the new complete map of fields
            val validatedFieldsMap = validateFieldInternal(fieldName, tempFieldsWithUpdate)

            // Pass the complete, validated map to the callback
            updateCallback(validatedFieldsMap)
        }
    }

    fun touched(fieldName: String) {
        if (fields.containsKey(fieldName)) {
            val fieldToTouch = fields[fieldName]
            if (fieldToTouch != null && !fieldToTouch.isTouched) {
                val updatedField = fieldToTouch.copy(isTouched = true)
                val tempFieldsWithUpdate = fields + (fieldName to updatedField)
                // Re-validate the field as its error display might change (showError depends on isTouched)
                val validatedFieldsMap = validateFieldInternal(fieldName, tempFieldsWithUpdate)
                updateCallback(validatedFieldsMap)
            }
        }
    }

    fun patch(
        updatedValues: Map<String, Any>, checkValid: Boolean = false // Changed name for clarity
    ) {
        var currentFields = this.fields
        updatedValues.forEach { (fieldName, fieldValue) ->
            if (currentFields.containsKey(fieldName)) {
                var updatedField = currentFields[fieldName]?.copy(value = fieldValue)
                if (checkValid) {
                    updatedField = updatedField?.copy(isTouched = true, isDirty = true)
                }
                currentFields = currentFields + (fieldName to updatedField!!)
                // Validate each patched field
                currentFields = validateFieldInternal(fieldName, currentFields)
            }
        }
        updateCallback(currentFields)
    }

    // Renamed to validateFieldInternal to avoid conflict if a public validateField is added
    // This function now returns the new map of fields instead of mutating `this.fields`
    private fun validateFieldInternal(
        fieldName: String,
        currentFieldsMap: Map<String, FormField<Any>>,
        isPristineUpdate: Boolean = false // Flag to potentially alter validation logic if needed
    ): Map<String, FormField<Any>> {
        val fieldToValidate = currentFieldsMap[fieldName]

        var isValid = true
        var error: String? = null

        // Only perform validation if the field is dirty or touched, or if it's a pristine update check
        // This logic might need adjustment based on desired validation behavior.
        // For now, always validate if called.
        if (fieldToValidate != null) {
            for (validation in fieldToValidate.validations) {
                val errorMessageKey = validation(fieldToValidate) // Pass the FormField<Any>
                if (errorMessageKey != null) {
                    isValid = false
                    error = fieldToValidate.messages[errorMessageKey] ?: "Invalid input"
                    break
                }
            }
        }

        val newValidatedField = fieldToValidate?.copy(isValid = isValid, error = error)
        return currentFieldsMap + (fieldName to newValidatedField!!)
    }


    private fun isValidForm(): Boolean {
        // Re-evaluate all fields' validity based on the current 'fields' map.
        // This ensures isFormValid is derived from the immutable 'fields' state.
        return fields.all {
            val field = it.value
            // A field is valid if its isValid flag is true.
            // We must ensure `validateFieldInternal` correctly sets this flag.
            field.isValid
        }
    }

    private fun checkFormTouched(): Boolean {
        return fields.any { it.value.isTouched }
    }

    // Removed validateForm method as its previous implementation mutated `this.fields`.
    // If a full form re-validation is needed, it should be triggered via FormBuilder
    // or a new method that processes `this.fields` immutably and uses `updateCallback`.
    // Example:
    // fun triggerFullRevalidation() {
    //     var revalidatedFields = this.fields
    //     this.fields.keys.forEach { fieldName ->
    //         revalidatedFields = validateFieldInternal(fieldName, revalidatedFields)
    //     }
    //     updateCallback(revalidatedFields)
    // }

    fun <T> getValue(fieldName: String): T? {
        return fields[fieldName]?.value as? T
    }

    fun getField(fieldName: String): FormField<Any> {
        return fields.getValue(fieldName) // getValue from Map throws if key not found
    }

    fun getAllValues(): Map<String, Any> {
        return fields.mapValues { it.value.value }
    }

    fun resetForm(form: Form) {
        // This should trigger an update through the callback with the new fields
        updateCallback(form.fields)
    }
}

data class FormField<T>(
    val value: T,
    val validations: List<(field: FormField<T>) -> String?> = emptyList(),
    val messages: Map<String, String> = mapOf(),
    val isValid: Boolean = false, // Default to false, validation should set it to true
    val isDirty: Boolean = false,
    val isTouched: Boolean = false,
    val error: String? = null,
) {

    var parent: Form? = null // This being a var is necessary for assignForm to work

    val errorMessage: String?
        get() = if (showError) error else null

    val showError: Boolean
        get() = (!isValid && isTouched)

    fun setParentValue(form: Form) {
        parent = form
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
