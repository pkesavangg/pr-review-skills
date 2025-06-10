package com.greatergoods.meapp.features.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import android.content.res.Configuration

@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel = hiltViewModel()
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeAppTheme.colorScheme.primaryAction),
    ) {
        // Main content
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "weightgurus is now",
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.subHeading1,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "my everyday",
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.heading2,
                fontWeight = FontWeight.W800,
                fontSize = 50.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "health",
                color = MeAppTheme.colorScheme.brand,
                style = MeAppTheme.typography.heading2,
                fontWeight = FontWeight.W800,
                fontSize = 50.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "LEARN MORE",
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.button1,
                fontWeight = FontWeight.W700,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = { /* TODO: Sign up action */ },
                modifier = Modifier
                    .widthIn(min = 150.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeAppTheme.colorScheme.primary,
                    contentColor = MeAppTheme.colorScheme.primaryAction,
                ),
            ) {
                Text(
                    text = "SIGN UP",
                    style = MeAppTheme.typography.button1,
                    fontWeight = FontWeight.W700,
                    fontSize = 16.sp,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
               loginViewModel.login(email = "vrengadevi@dmdbrands.com", password = "123456")
                },
                modifier = Modifier
                    .widthIn(min = 150.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MeAppTheme.colorScheme.primaryAction,
                    contentColor = MeAppTheme.colorScheme.inverse,
                ),
                border = BorderStroke(
                    width = 3.dp,
                    color = MeAppTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "LOG IN",
                    style = MeAppTheme.typography.button1,
                    fontWeight = FontWeight.W700,
                    fontSize = 16.sp,
                )
            }
        }
        // Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "me.health by greater goods",
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.subHeading2,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "version 1.0.0",
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.subHeading2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun LoginScreenPreviewLight() {
    MeAppTheme {
        LoginScreen()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun LoginScreenPreviewDark() {
    MeAppTheme {
        LoginScreen()
    }
}
