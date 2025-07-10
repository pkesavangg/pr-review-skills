package com.greatergoods.meapp.features.integration.model

import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider

/**
 * OAuth flow state management.
 * @property isOAuthInProgress Whether OAuth flow is currently in progress.
 * @property currentProvider The provider for the current OAuth flow.
 * @property oAuthUrl The OAuth URL to open.
 * @property oAuthError Error message if OAuth flow failed.
 * @property accountId The account ID for OAuth state parameter.
 */
data class OAuthState(
  val isOAuthInProgress: Boolean = false,
  val currentProvider: IntegrationProvider? = null,
  val oAuthUrl: String? = null,
  val oAuthError: String? = null,
  val accountId: String? = null,
) {
  companion object {
    val Idle = OAuthState()

    /**
     * Creates OAuth state for starting OAuth flow.
     */
    fun startOAuth(
      provider: IntegrationProvider,
      accountId: String,
    ): OAuthState =
      OAuthState(
        isOAuthInProgress = true,
        currentProvider = provider,
        oAuthUrl = provider.getOAuthUrl(accountId),
        oAuthError = null,
        accountId = accountId,
      )
  }
}
