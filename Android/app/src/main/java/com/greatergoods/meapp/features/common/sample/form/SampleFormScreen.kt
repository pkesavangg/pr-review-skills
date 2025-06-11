package com.greatergoods.meapp.features.common.sample.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.input.BodyCompInput
import com.greatergoods.meapp.features.common.components.input.PasswordInput
import com.greatergoods.meapp.features.common.components.input.SkuInput
import com.greatergoods.meapp.features.common.components.input.TextInput
import com.greatergoods.meapp.features.common.components.input.WeightInput
import com.greatergoods.meapp.features.common.helper.form.FormBuilder
import com.greatergoods.meapp.features.common.helper.form.FormField
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.helper.form.ValidationType
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun SampleFormScreen() {
    val formBuilder = remember {
        FormBuilder(
            mapOf(
                "email" to FormField(
                    value = "",
                    validations = listOf(
                        FormValidations.required(),
                        FormValidations.email()
                    ),
                    messages = mapOf(
                        ValidationType.REQUIRED to "Email is required",
                        ValidationType.EMAIL to "Please enter a valid email"
                    )
                ),
                "password" to FormField(
                    value = "",
                    validations = listOf(
                        FormValidations.required(),
                        FormValidations.minLength(8)
                    ),
                    messages = mapOf(
                        ValidationType.REQUIRED to "Password is required",
                        ValidationType.MIN_LENGTH to "Password must be at least 8 characters"
                    )
                ),
                "confirmPassword" to FormField(
                    value = "",
                    validations = listOf(
                        FormValidations.required(),
                        FormValidations.matchPassword("password")
                    ),
                    messages = mapOf(
                        ValidationType.REQUIRED to "Please confirm your password",
                        ValidationType.MATCH_PASSWORD to "Passwords do not match"
                    )
                ),
                "weight" to FormField(
                    value = "",
                    validations = listOf(
                        FormValidations.weightValidator(unitType = "kg")
                    ),
                    messages = mapOf(
                        ValidationType.REQUIRED to "Weight is required",
                        ValidationType.NOT_IN_RANGE to "Weight must be between 1 and 450 kg"
                    )
                ),
                "bodyComp" to FormField(
                    value = "",
                    validations = listOf(
                        FormValidations.bodyCompValidator(min = 0, max = 100)
                    ),
                    messages = mapOf(
                        ValidationType.REQUIRED to "Body composition is required",
                        ValidationType.NOT_IN_RANGE to "Body composition must be between 0 and 99"
                    )
                ),
                "sku" to FormField(
                    value = "",
                    validations = listOf(
                        FormValidations.skuValidator()
                    ),
                    messages = mapOf(
                        ValidationType.PATTERN to "SKU must be 4 digits"
                    )
                )
            )
        )
    }

    val form by formBuilder.form.collectAsState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    com.greatergoods.meapp.theme.MeAppTheme {
        Column(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    focusManager.clearFocus()
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sample Form",
                style = MeAppTheme.typography.heading3
            )

            TextInput(
                formControl = form.getField("email"),
                name = "email",
                label = "Email",
                placeHolder = "Enter your email"
            )

            PasswordInput(
                formControl = form.getField("password"),
                name = "password",
                label = "Password",
                placeHolder = "Enter your password"
            )

            PasswordInput(
                formControl = form.getField("confirmPassword"),
                name = "confirmPassword",
                label = "Confirm Password",
                placeHolder = "Confirm your password"
            )

            WeightInput(
                formControl = form.getField("weight"),
                name = "weight",
                label = "Weight",
                placeHolder = "Enter weight in kg"
            )

            BodyCompInput(
                formControl = form.getField("bodyComp"),
                name = "bodyComp",
                label = "Body Composition",
                placeHolder = "Enter body composition %"
            )

            SkuInput(
                formControl = form.getField("sku"),
                name = "sku",
                label = "SKU",
                placeHolder = "Enter 4-digit SKU"
            )

            Button(
                onClick = {
                    if (form.isFormValid) {
                        // Handle form submission
                        val formData = form.getAllValues()
                        // Do something with formData
                    }
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text("Submit")
            }

            if (!form.isFormValid) {
                Text(
                    text = "Form Invalid",
                    style = MeAppTheme.typography.body2,
                    color = MeAppTheme.colorScheme.error
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
