package com.greatergoods.meapp.features.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.login.LoginViewModel.LoginState
import com.greatergoods.meapp.features.login.LoginViewModel.RefreshTokenState

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf("") }

    val loginState by viewModel.loginState.collectAsState()
    val refreshTokenState by viewModel.refreshTokenState.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Login Section
        Text(
            text = "Login",
            style = MaterialTheme.typography.headlineMedium,
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = loginState !is LoginState.Loading,
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Login")
            }
        }

        // Refresh Token Section
        Text(
            text = "Refresh Token",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 32.dp),
        )

        OutlinedTextField(
            value = refreshToken,
            onValueChange = { refreshToken = it },
            label = { Text("Refresh Token") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { viewModel.refreshToken(refreshToken) },
            modifier = Modifier.fillMaxWidth(),
            enabled = refreshTokenState !is RefreshTokenState.Loading,
        ) {
            if (refreshTokenState is RefreshTokenState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Refresh Token")
            }
        }

        // State Display
        when (loginState) {
            is LoginState.Success -> {
                Text(
                    text = "Login successful!",
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is LoginState.Error -> {
                Text(
                    text = (loginState as LoginState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> {}
        }

        when (refreshTokenState) {
            is RefreshTokenState.Success -> {
                Text(
                    text = "Token refreshed successfully!",
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is RefreshTokenState.Error -> {
                Text(
                    text = (refreshTokenState as RefreshTokenState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> {}
        }
    }
}
