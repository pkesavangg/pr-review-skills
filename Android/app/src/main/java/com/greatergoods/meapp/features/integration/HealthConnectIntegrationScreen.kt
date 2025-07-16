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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.greatergoods.meapp.features.integration.model.HealthConnectAction
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
    viewModel: HealthConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()

    HealthConnectIntegrationContent(
        state = state,
        handleIntent = viewModel::handleIntent,
        onBack = {
            coroutineScope.launch {
                viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.EXIT))
            }
        },
        onDismiss = {
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
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentSlide,
        pageCount = { HealthConnectSetup.entries.size }
    )
    val scrollState = rememberScrollState()

    // Update pager when state changes
    LaunchedEffect(state.healthConnectSetupState) {
        val targetPage = state.healthConnectSetupState.code
        if (targetPage >= 0 && targetPage < HealthConnectSetup.entries.size) {
            pagerState.animateScrollToPage(targetPage)
            handleIntent(HealthConnectIntent.UpdateSlide(targetPage))
        }
    }

    // Handle back button
    BackHandler(enabled = !state.alertPresented) {
        handleIntent.invoke(HealthConnectIntent.ConfirmExitSetup)
    }

    AppScaffold(
        title = "",
        containerColor = MeTheme.colorScheme.secondaryBackground,
        appBarColor = MeTheme.colorScheme.secondaryBackground,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
              handleIntent.invoke(HealthConnectIntent.ConfirmExitSetup)
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
                                handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
                            },
                        )
                    }
                    HealthConnectSetup.FINISH_CONNECT -> {
                        FinishConnect(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
                                onDismiss()
                            }
                        )
                    }

                    HealthConnectSetup.CANCEL_CONNECT -> {
                        PermissionLimitScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT))
                                onDismiss()
                            },
                            onSecondaryAction = {
                                handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.FINISH))
                            }
                        )
                    }
                    HealthConnectSetup.PERMISSION_LIMIT -> {
                        PermissionLimitScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT))
                            },
                            onSecondaryAction = {
                                handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.EXIT))
                            }
                        )
                    }
                    HealthConnectSetup.USER_CONFLICT -> {
                        UserConflictScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.EXIT))
                            },
                        )
                    }

                    HealthConnectSetup.COMPLETE_RECONNECTION -> {
                        CompleteReconnectionScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
                            },
                        )
                    }

                    HealthConnectSetup.INCOMPLETE_RECONNECTION -> {
                        IncompleteReconnectionScreen(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.UPDATE_PERMISSIONS))
                            },
                            onSecondaryAction = {
                                handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.SKIP))
                            }
                        )
                    }

                    HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION -> {
                        FinishConnect (
                            title = HealthConnectStrings.FinishPartialReconnectStrings.Title,
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
                                onDismiss()
                            },
                        )
                    }
                    else -> {
                        // Default to start connect for any other state
                        StartConnect(
                            onPrimaryAction = {
                                handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
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
                onBack = {},
                onDismiss = {}
            )
        }
    }
}
