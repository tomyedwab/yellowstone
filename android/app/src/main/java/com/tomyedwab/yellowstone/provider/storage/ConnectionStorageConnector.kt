package com.tomyedwab.yellowstone.provider.storage

import android.content.Context
import androidx.lifecycle.Observer
import com.tomyedwab.yellowstone.provider.connection.ConnectionAction
import com.tomyedwab.yellowstone.provider.connection.ConnectionStateProvider
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState

class ConnectionStorageConnector(
    private val context: Context,
    private val connectionStateProvider: ConnectionStateProvider
) {
    private val storageProvider = ConnectionStorageProvider(context)
    private var connectionStateObserver: Observer<HubConnectionState>? = null

    fun initialize() {
        loadInitialAccountData()
        observeConnectionState()
    }

    fun cleanup() {
        connectionStateObserver?.let { observer ->
            connectionStateProvider.connectionState.removeObserver(observer)
        }
    }

    private fun loadInitialAccountData() {
        val accountList = storageProvider.loadHubAccounts()
        val currentState = connectionStateProvider.connectionState.value

        // Only dispatch AccountListLoaded if we're in Uninitialized state
        // This prevents resetting an active connection when reconnecting to the service
        if (currentState is HubConnectionState.Uninitialized) {
            connectionStateProvider.dispatch(ConnectionAction.AccountListLoaded(accountList))

            accountList.selectedAccount?.let { selectedAccountId ->
                connectionStateProvider.dispatch(ConnectionAction.ConnectionSelected(selectedAccountId))
            }
        }
    }

    private fun observeConnectionState() {
        connectionStateObserver = Observer<HubConnectionState> { state ->
            handleStateChange(state)
        }

        connectionStateProvider.connectionState.observeForever(connectionStateObserver!!)
    }

    private fun handleStateChange(state: HubConnectionState) {
        when (state) {
            is HubConnectionState.Connecting.LoggingIn -> {
                clearSelectionOnLoginStart()
            }
            is HubConnectionState.Connected -> {
                saveSuccessfulConnection(state)
            }
            is HubConnectionState.WaitingForLogin -> {
                // Check if any account lost its refresh token and persist that change
                handleRefreshTokenChanges(state)
            }
            else -> {}
        }
    }

    private fun handleRefreshTokenChanges(state: HubConnectionState.WaitingForLogin) {
        val currentStoredAccounts = storageProvider.loadHubAccounts()

        // Find accounts that had a refresh token in storage but don't have one in the current state
        state.accountList.accounts.forEach { stateAccount ->
            val storedAccount = currentStoredAccounts.accounts.find { it.id == stateAccount.id }
            if (storedAccount != null && storedAccount.refreshToken != null && stateAccount.refreshToken == null) {
                // The account lost its refresh token, persist this change
                storageProvider.clearRefreshToken(stateAccount.id)
            }
        }
    }

    private fun clearSelectionOnLoginStart() {
        val currentAccountList = storageProvider.loadHubAccounts()
        if (currentAccountList.selectedAccount != null) {
            storageProvider.clearSelectedAccount()
        }
    }

    private fun saveSuccessfulConnection(state: HubConnectionState.Connected) {
        storageProvider.saveHubAccount(
            hubUrl = state.loginAccount.url,
            username = state.loginAccount.name,
            refreshToken = state.refreshToken
        )
    }
}
