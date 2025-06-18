package com.greatergoods.meapp.features.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.landing.strings.LandingString
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun LandingScreen() {
    val backStack = LocalNavBackStack.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeTheme.colorScheme.primaryAction),
    ) {
        // Main content
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = MeTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "weightgurus is now",
                color = MeTheme.colorScheme.inverseAction,
                style = MeTheme.typography.subHeading1,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
            Text(
                text = "my everyday",
                color = MeTheme.colorScheme.inverseAction,
                style = MeTheme.typography.heading2,
                fontWeight = FontWeight.W800,
                fontSize = 50.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "health",
                color = MeTheme.colorScheme.meAppPrimary,
                style = MeTheme.typography.heading2,
                fontWeight = FontWeight.W800,
                fontSize = 50.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.md))
            Text(
                text = "LEARN MORE",
                color = MeTheme.colorScheme.inverseAction,
                style = MeTheme.typography.button1,
                fontWeight = FontWeight.W700,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.x6l))
            AppButton(
                type = ButtonType.SecondaryFilled,
                label = LandingString.SignUp,
                size = ButtonSize.Large,
                onClick = { /* TODO: Sign up action */ },
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
            AppButton(
                type = ButtonType.SecondaryOutlined, label = LandingString.Login,
                size = ButtonSize.Large,
                onClick = {
                    backStack.addRoute(
                        AppRoute.Auth.Login,
                    )
                },
            )

        }
        // Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
        ) {
            Text(
                text = "me.health by greater goods",
                color = MeTheme.colorScheme.inverseAction,
                style = MeTheme.typography.subHeading2,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "version 1.0.0",
                color = MeTheme.colorScheme.inverseAction,
                style = MeTheme.typography.subHeading2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PreviewTheme
@Composable
fun LoginScreenPreviewLight() {
    MeAppTheme {
        LandingScreen()
    }
}
