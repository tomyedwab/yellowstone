package com.tomyedwab.yellowstone.provider.storage

import android.content.Context
import androidx.lifecycle.Observer
import com.tomyedwab.yellowstone.provider.connection.ConnectionAction
import com.tomyedwab.yellowstone.provider.connection.ConnectionState
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
        connectionStateProvider.dispatch(ConnectionAction.AccountListLoaded(accountList))

        accountList.selectedAccount?.let { selectedAccountId ->
            connectionStateProvider.dispatch(ConnectionAction.ConnectionSelected(selectedAccountId))
        }
    }

    private fun observeConnectionState() {
        connectionStateObserver = Observer<HubConnectionState> { state ->
            handleStateChange(state)
        }

        connectionStateProvider.connectionState.observeForever(connectionStateObserver!!)
    }

    private fun handleStateChange(state: HubConnectionState) {
        when {
            isStartingLogin(state) -> {
                clearSelectionOnLoginStart(state)
            }
            isSuccessfullyConnected(state) -> {
                saveSuccessfulConnection(state)
            }
        }
    }

    private fun isStartingLogin(state: HubConnectionState): Boolean {
        return state.state == ConnectionState.CONNECTING &&
               state.loginPassword != null &&
               state.loginAccount != null
    }

    private fun isSuccessfullyConnected(state: HubConnectionState): Boolean {
        return state.state == ConnectionState.CONNECTED &&
               state.loginAccount != null &&
               state.refreshToken != null
    }

    private fun clearSelectionOnLoginStart(state: HubConnectionState) {
        val currentAccountList = storageProvider.loadHubAccounts()
        if (currentAccountList.selectedAccount != null) {
            storageProvider.clearSelectedAccount()
        }
    }

    private fun saveSuccessfulConnection(state: HubConnectionState) {
        val loginAccount = state.loginAccount ?: return
        val refreshToken = state.refreshToken ?: return

        val savedAccount = storageProvider.saveHubAccount(
            hubUrl = loginAccount.url,
            username = loginAccount.name,
            refreshToken = refreshToken
        )
    }
}
