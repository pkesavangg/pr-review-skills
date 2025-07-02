package com.greatergoods.meapp.features.landing.screen

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.landing.strings.LandingString
import com.greatergoods.meapp.features.loading.string.LoadingString
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.launch
import android.app.Activity

@Composable
fun LandingScreen() {
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MeTheme.colorScheme.primaryAction),
    ) {
        // Main content
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = MeTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = AppIcons.Default.Banner),
                contentDescription = LoadingString.LOADING,
                colorFilter = ColorFilter.tint(MeTheme.colorScheme.inverseAction),
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.x6l))
            AppButton(
                type = ButtonType.SecondaryFilled,
                label = LandingString.SignUp,
                size = ButtonSize.Large,
                onClick = {
                    coroutineScope.launch {
                        backStack.addRoute(
                            AppRoute.Auth.Signup,
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
            AppButton(
                type = ButtonType.SecondaryOutlined,
                label = LandingString.Login,
                size = ButtonSize.Large,
                onClick = {
                    coroutineScope.launch {
                        backStack.addRoute(
                            AppRoute.Auth.Login(),
                        )
                    }
                },
            )
        }
        // Footer
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = MeTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
        ) {
            Text(
                text = LandingString.Version,
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
