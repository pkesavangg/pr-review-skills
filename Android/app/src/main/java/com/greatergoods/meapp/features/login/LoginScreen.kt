package com.greatergoods.meapp.features.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.login.LoginViewModel.ProfileState
import com.greatergoods.meapp.features.login.LoginViewModel.LoginState
import com.greatergoods.meapp.features.login.LoginViewModel.RefreshTokenState




@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val loginState by viewModel.loginState.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val refreshTokenState by viewModel.refreshTokenState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 25.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Login Section
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }

        // Profile Section
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Profile Section",
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = { viewModel.fetchProfile() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fetch Profile")
                }

                // Profile State Display
                when (val state = profileState) {
                    is ProfileState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is ProfileState.Success -> {
                        Text("Profile: ${state.profile.firstName} ${state.profile.lastName}")
                        Text("Email: ${state.profile.email}")
                        state.profile.height?.let { Text("Height: $it") }
                        state.profile.weight?.let { Text("Weight: $it") }
                    }
                    is ProfileState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
        }

        // Token Refresh Section
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Token Refresh Section",
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = {
                        // Get refresh token from login response
                        (loginState as? LoginState.Success)?.response?.refreshToken?.let { token ->
                            viewModel.refreshToken(token)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loginState is LoginState.Success
                ) {
                    Text("Refresh Token")
                }

                // Token Refresh State Display
                when (val state = refreshTokenState) {
                    is RefreshTokenState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is RefreshTokenState.Success -> {
                        Text("Token Refreshed Successfully")
                    }
                    is RefreshTokenState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
        }

        // Login State Display
        when (val state = loginState) {
            is LoginState.Loading -> {
                CircularProgressIndicator()
            }
            is LoginState.Success -> {
                Text("Login Successful")
            }
            is LoginState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }
    }
}
