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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.features.MyAccounts.strings.MyAccountsScreenStrings
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppUserList
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.landing.reducer.MultiAccountLandingIntent
import com.greatergoods.meapp.features.landing.reducer.MultiAccountLandingState
import com.greatergoods.meapp.features.landing.viewmodel.MultiAccountLandingViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.borderRadius
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * MultiAccountLandingScreen displays the app logo, subtitle, and a scrollable list of up to 5 accounts, with an Add Account button.
 */
@Composable
fun MultiAccountLandingScreen() {
    val viewModel: MultiAccountLandingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()


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
                    onDeleteRequest = {},
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

