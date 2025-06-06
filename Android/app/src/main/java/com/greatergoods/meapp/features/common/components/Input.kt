package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun Input(
    label: String,
    formControl: FormControl<String>,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    showAllErrors: Boolean = false,
) {
    var everFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = formControl.value,
        onValueChange = { formControl.onValueChange(it) },
        label = { Text(label) },
        isError = (formControl.error != null) && (formControl.touched || showAllErrors),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        everFocused = true
                    }
                    // Only call onBlur() if the field was ever focused and is now not focused
                    if (everFocused && !focusState.isFocused) {
                        formControl.onBlur()
                    }
                },
    )
    if ((formControl.touched || showAllErrors) && formControl.error != null) {
        Text(
            formControl.error!!,
            color = Color.Red,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (formControl.pending) {
        Text(
            "Checking...",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@PreviewTheme
@Composable
fun InputPreview() {
    MeAppTheme {
        val fakeScope = rememberCoroutineScope()
        val formControl = FormControl("", scope = fakeScope)
        Input("Label", formControl)
    }
}
