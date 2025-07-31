package com.dmdbrands.gurus.weight.domain.services

/**
 * Service interface for handling offline data synchronization.
 * Manages offline operations and syncs them when connectivity is restored.
 */
interface IOfflineHandlerService {
    /**
     * Handles offline data synchronization when network connectivity is restored.
     * Syncs all pending offline data including profile updates, goals, weightless settings, etc.
     */
    suspend fun handleOfflineSync()
}
