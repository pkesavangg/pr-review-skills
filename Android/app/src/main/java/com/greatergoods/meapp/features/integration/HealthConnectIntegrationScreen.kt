package com.greatergoods.meapp.features.integration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.integration.components.CompleteReconnectionScreen
import com.greatergoods.meapp.features.integration.components.FinishConnect
import com.greatergoods.meapp.features.integration.components.IncompleteReconnectionScreen
import com.greatergoods.meapp.features.integration.components.PermissionLimitScreen
import com.greatergoods.meapp.features.integration.components.StartConnect
import com.greatergoods.meapp.features.integration.components.UserConflictScreen
import com.greatergoods.meapp.features.integration.model.HealthConnectIntent
import com.greatergoods.meapp.features.integration.model.HealthConnectSetup
import com.greatergoods.meapp.features.integration.model.HealthConnectUiState
import com.greatergoods.meapp.features.integration.strings.HealthConnectStrings
import com.greatergoods.meapp.features.integration.viewmodel.HealthConnectViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.launch

/**
 * Main Health Connect integration screen that manages the integration flow.
 */
@Composable
fun HealthConnectIntegrationScreen(
    viewModel: HealthConnectViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()

    HealthConnectIntegrationContent(
        state = state,
        handleIntent = viewModel::handleIntent,
        onBack = {
            coroutineScope.launch {
                backStack.removeLast()
            }
        }
    )
}

/**
 * Content composable for the Health Connect integration screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HealthConnectIntegrationContent(
    state: HealthConnectUiState,
    handleIntent: (HealthConnectIntent) -> Unit,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState { HealthConnectSetup.entries.size }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    BackHandler {
        onBack()
    }

    AppScaffold(
        title = "",
        containerColor = MeTheme.colorScheme.secondaryBackground,
        appBarColor = MeTheme.colorScheme.secondaryBackground,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                onBack()
            }
        },
        borderColor = Color.Transparent
    ) { paddingModifier ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(vertical = MeTheme.spacing.md)
            ) {
                when (state.healthConnectSetupState) {
                    HealthConnectSetup.START_CONNECT -> {
                        StartConnect(
                            onPrimaryAction = {
                                scope.launch {
                                    handleIntent(HealthConnectIntent.Connect)
                                    pagerState.animateScrollToPage(HealthConnectSetup.FINISH_CONNECT.code)
                                }
                            },
                        )
                    }
                    HealthConnectSetup.FINISH_CONNECT -> {
                        FinishConnect(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.Finish)
                                onBack()
                            }
                        )
                    }

                    HealthConnectSetup.CANCEL_CONNECT -> {
                        PermissionLimitScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.Finish)
                                onBack()
                            },
                            onSecondaryAction = {
                                handleIntent(HealthConnectIntent.OpenHealthConnect)
                            }
                        )
                    }
                    HealthConnectSetup.PERMISSION_LIMIT -> {
                        PermissionLimitScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.Connect)
                            },
                            onSecondaryAction = {
                                handleIntent(HealthConnectIntent.OpenHealthConnect)
                            }
                        )
                    }
                    HealthConnectSetup.USER_CONFLICT -> {
                        UserConflictScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.Connect)
                            },
                        )
                    }

                    HealthConnectSetup.COMPLETE_RECONNECTION -> {
                        CompleteReconnectionScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.Connect)
                            },
                        )
                    }

                    HealthConnectSetup.INCOMPLETE_RECONNECTION -> {
                        IncompleteReconnectionScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.Connect)
                            },
                            onSecondaryAction = {
                                handleIntent(HealthConnectIntent.Skip)
                            }
                        )
                    }

                    HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION -> {
                        FinishConnect (
                            title = HealthConnectStrings.FinishPartialReconnectStrings.Title,
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.Finish)
                            },
                        )
                    }
                    else -> {
                        // Default to start connect for any other state
                        StartConnect(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.Connect)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HealthConnectIntegrationScreenPreview() {
    MeAppTheme {
        Surface {
            HealthConnectIntegrationContent(
                state = HealthConnectUiState(),
                handleIntent = {},
                onBack = {}
            )
        }
    }
}
