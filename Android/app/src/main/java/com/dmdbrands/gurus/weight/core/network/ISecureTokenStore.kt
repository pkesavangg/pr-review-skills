package com.dmdbrands.gurus.weight.core.network

import com.dmdbrands.gurus.weight.domain.model.api.user.Token

interface ISecureTokenStore {
    fun saveToken(accountId: String, token: Token)
    fun getToken(accountId: String): Token?
    fun getAllTokens(): Map<String, Token>
    fun removeToken(accountId: String)
    fun clearAll()
    fun hasTokens(): Boolean

    /** Number of consecutive runtime encryption failures (persisted in plain prefs). */
    fun getEncryptionFailureCount(): Int
    fun incrementEncryptionFailureCount()
    fun resetEncryptionFailureCount()
}
