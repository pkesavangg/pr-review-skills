package com.greatergoods.meapp.features.common.sample.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.features.common.components.InputField
import com.greatergoods.meapp.features.common.components.InputType

@Composable
fun FormSample(viewModel: FormSampleViewModel = viewModel()) {
    val controls = viewModel.controls
    val form = viewModel.form

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
    ) {
        InputField(
            formControl = controls.username,
            label = "Email",
            name = "email",
            type = InputType.EMAIL,
            placeHolder = "Enter your email"
        )
        Spacer(Modifier.height(12.dp))
        InputField(
            formControl = controls.password,
            label = "Password",
            name = "password",
            type = InputType.PASSWORD,
            placeHolder = "Enter your password"
        )
        Spacer(Modifier.height(12.dp))
        InputField(
            formControl = controls.confirmPassword,
            label = "Confirm Password",
            name = "confirmPassword",
            type = InputType.PASSWORD,
            placeHolder = "Re-enter your password"
        )

        if (form.groupError != null &&
            (controls.confirmPassword.touched || controls.password.touched)
        ) {
            Text(form.groupError!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.validateForm() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Register")
        }
    }
}

@PreviewTheme()
@Composable
fun FormSamplePreview() {
    MeAppTheme {
        FormSample()
    }
}
