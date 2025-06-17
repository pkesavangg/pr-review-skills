package com.greatergoods.meapp.features.common.sample.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.helper.form.ValidationType
import com.greatergoods.meapp.theme.MeAppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme
import com.greatergoods.meapp.theme.MeAppTheme.typography

data class SampleControls(
    val email: FormControl<String>,
    val password: FormControl<String>,
    val confirmPassword: FormControl<String>,
    val weight: FormControl<String>,
    val bodyComp: FormControl<String>,
    val sku: FormControl<String>,
    val disabledField: FormControl<String>
)

@Composable
fun SampleFormScreen() {
    val scope = rememberCoroutineScope()
    val email = remember { FormControl("", listOf(FormValidations.required(), FormValidations.email()), emptyList(), scope) }
    val password = remember { FormControl("", listOf(FormValidations.required(), FormValidations.minLength(8)), emptyList(), scope) }
    val confirmPassword = remember { FormControl("", listOf(FormValidations.required(), FormValidations.confirmPasswordValidator(password)), emptyList(), scope) }
    val weight = remember { FormControl("", listOf(FormValidations.weightValidator("kg")), emptyList(), scope) }
    val bodyCompDecimal = remember { FormControl("", listOf(FormValidations.bodyCompValidator()), emptyList(), scope) }
    val bodyComp = remember { FormControl("", listOf(FormValidations.bodyCompValidator()), emptyList(), scope) }
    val sku = remember { FormControl("", listOf(FormValidations.skuValidator()), emptyList(), scope) }
    val disabledField = remember { FormControl("", emptyList(), emptyList(), scope) }
    val controls = SampleControls(email, password, confirmPassword, weight, bodyComp, sku, disabledField)
    val formGroup = remember { FormGroup(controls) }

    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    MeAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { focusManager.clearFocus() }
                ).background(color = colorScheme.overlay)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sample Form",
                style = typography.heading3,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            AppInput(formControl = email, type = AppInputType.TEXT, label = "Email")
            AppInput(formControl = password, type = AppInputType.PASSWORD, label = "Password", placeHolder = "Enter your password")
            AppInput(formControl = confirmPassword, type = AppInputType.PASSWORD, label = "Confirm Password")
            AppInput(formControl = weight, type = AppInputType.WEIGHT, label = "Weight")
            AppInput(formControl = bodyCompDecimal, type = AppInputType.BODY_COMP_DECIMAL, label = "Body Composition Decimal",)
            AppInput(formControl = bodyComp, type = AppInputType.BODY_COMP, label = "Body Composition",)
            AppInput(formControl = sku, type = AppInputType.NUMBER, label = "SKU", )
            AppInput(formControl = disabledField, type = AppInputType.TEXT, label = "Disabled Field", enabled = false)
            Button(
                onClick = {
                    formGroup.forceShowAllErrors()
                    formGroup.validate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text("Submit")
            }
            if (formGroup.groupError != null) {
                Text(
                    text = formGroup.groupError!!,
                    style = typography.body2,
                    color = colorScheme.error
                )
            }
        }
    }
}

@Composable
fun SampleFormScreenPreview() {
    MeAppTheme {
        SampleFormScreen()
    }
}
