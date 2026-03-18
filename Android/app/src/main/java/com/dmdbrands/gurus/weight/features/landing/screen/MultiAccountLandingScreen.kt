package com.dmdbrands.gurus.weight.features.landing.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.MyAccounts.strings.MyAccountsScreenStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppUserList
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.landing.reducer.MultiAccountLandingIntent
import com.dmdbrands.gurus.weight.features.landing.reducer.MultiAccountLandingState
import com.dmdbrands.gurus.weight.features.landing.viewmodel.MultiAccountLandingViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * MultiAccountLandingScreen displays the app logo, subtitle, and a scrollable list of up to 5 accounts, with an Add Account button.
 */
@Composable
fun MultiAccountLandingScreen() {
    val viewModel: MultiAccountLandingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()


    MultiAccountLandingScreenContent(
        state = state,
        viewModel::handleIntent,
    )
}

@Composable
fun MultiAccountLandingScreenContent(
    state: MultiAccountLandingState,
    handleIntent: (MultiAccountLandingIntent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeTheme.colorScheme.secondaryBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = AppIcons.Default.Banner),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MeTheme.colorScheme.textBody),
            )
            Spacer(Modifier.height(spacing.x3l))
            // Account list (max 5 in viewport, scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(borderRadius.sm))
                    .background(MeTheme.colorScheme.secondaryBackground),
            ) {
                AppUserList(
                    accounts = state.accounts,
                    showAccountActivity = false,
                    maxVisibleItems = 5,
                    canRemoveAccount = true,
                    onDeleteRequest = { handleIntent(MultiAccountLandingIntent.RequestRemoveAccount(it)) },
                    onAccountSelect = { handleIntent(MultiAccountLandingIntent.SelectAccount(it)) },
                    onLoginRequest = { handleIntent(MultiAccountLandingIntent.Login(it)) },
                )
            }
            Spacer(Modifier.height(spacing.x3l))
            AppButton(
                label = MyAccountsScreenStrings.LogIntoExistingAccount,
                type = ButtonType.PrimaryOutlined,
                onClick = { handleIntent(MultiAccountLandingIntent.Login()) },
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            AppButton(
                label = MyAccountsScreenStrings.CreateNewAccount,
                type = ButtonType.TextPrimary,
                onClick = { handleIntent(MultiAccountLandingIntent.CreateAccount) },
            )
        }
    }
}

@PreviewTheme
@Composable
fun MultiAccountLandingScreenPreview() {
    MeAppTheme {
        MultiAccountLandingScreenContent(
            state = MultiAccountLandingState(
                accounts = listOf(
                    Account(
                        id = "1",
                        firstName = "Kristin",
                        lastName = "",
                        dob = "",
                        email = "kristin@gmail.com",
                        gender = "F",
                        isActiveAccount = true,
                        isLoggedIn = true,
                        lastActiveTime = null,
                        zipcode = "",
                        isSynced = true,
                        isExpired = false,
                        weightUnit = WeightUnit.LB,
                        isWeightlessOn = false,
                    height = 0,
                    activityLevel = null,
                    weightlessTimestamp = null,
                    weightlessWeight = null,
                    isStreakOn = false,
                    dashboardType = null,
                    dashboardMetrics = null,
                    ),
                    Account(
                        id = "2",
                        firstName = "William",
                        lastName = "",
                        dob = "",
                        email = "william@gmail.com",
                        gender = "M",
                        isActiveAccount = false,
                        isLoggedIn = true,
                        lastActiveTime = null,
                        zipcode = "",
                        isSynced = true,
                        isExpired = false,
                        weightUnit = WeightUnit.LB,
                        isWeightlessOn = false,
                        height = 0,
                        activityLevel = null,
                        weightlessTimestamp = null,
                        weightlessWeight = null,
                        isStreakOn = false,
                        dashboardType = null,
                        dashboardMetrics = null,
                    ),
                    Account(
                        id = "3",
                        firstName = "Jacob",
                        lastName = "",
                        dob = "",
                        email = "jacob@gmail.com",
                        gender = "M",
                        isActiveAccount = false,
                        isLoggedIn = true,
                        lastActiveTime = null,
                        zipcode = "",
                        isSynced = true,
                        isExpired = false,
                        weightUnit = WeightUnit.LB,
                        isWeightlessOn = false,
                        height = 0,
                        activityLevel = null,
                        weightlessTimestamp = null,
                        weightlessWeight = null,
                        isStreakOn = false,
                        dashboardType = null,
                        dashboardMetrics = null,
                    ),
                ),
            ),
            {},
        )
    }
}

