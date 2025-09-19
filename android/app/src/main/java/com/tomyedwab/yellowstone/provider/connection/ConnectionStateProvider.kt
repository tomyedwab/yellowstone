package com.tomyedwab.yellowstone.provider.connection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ConnectionStateProvider : ViewModel() {
    private val _connectionState = MutableLiveData(HubConnectionState())
    val connectionState: LiveData<HubConnectionState> = _connectionState
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCleared() {
        super.onCleared()
        mainScope.cancel()
    }

    fun dispatch(action: ConnectionAction) {
        val currentState = _connectionState.value ?: HubConnectionState()
        val newState = connectionStateReducer(currentState, action)

        // Ensure we're on the main thread when updating LiveData
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            _connectionState.value = newState
        } else {
            mainScope.launch {
                _connectionState.value = newState
            }
        }
    }

    private fun connectionStateReducer(
        state: HubConnectionState,
        action: ConnectionAction
    ): HubConnectionState {
        return when (action) {
            is ConnectionAction.AccountListLoaded -> {
                state.copy(
                    accountList = action.accountList,
                    state = if (action.accountList.accounts.isNotEmpty()) {
                        ConnectionState.NO_SELECTION
                    } else {
                        ConnectionState.LOGIN
                    },
                    loginAccount = null,
                    loginPassword = null,
                    refreshToken = null,
                    accessToken = null,
                    backendComponentIDs = null,
                    backendEventIDs = null,
                    connectionError = null,
                    pendingEvents = emptyList()
                )
            }

            is ConnectionAction.ConnectionSelected -> {
                val accountList = state.accountList ?: return state
                val nextAccount = accountList.accounts.find { account ->
                    account.id == action.selectedAccount
                } ?: return state

                state.copy(
                    accountList = accountList.copy(selectedAccount = action.selectedAccount),
                    state = ConnectionState.CONNECTING,
                    loginAccount = nextAccount,
                    refreshToken = nextAccount.refreshToken,
                    accessToken = null,
                )
            }

            is ConnectionAction.StartLogin -> {
                state.copy(
                    state = ConnectionState.CONNECTING,
                    loginAccount = action.account,
                    loginPassword = action.password,
                    refreshToken = null,
                    accessToken = null,
                    backendComponentIDs = null,
                    backendEventIDs = null,
                    connectionError = null,
                    pendingEvents = emptyList()
                )
            }

            is ConnectionAction.StartConnection -> {
                state.copy(
                    state = ConnectionState.CONNECTING,
                    loginAccount = action.account,
                    loginPassword = null,
                    refreshToken = action.refreshToken
                )
            }

            is ConnectionAction.ReceivedAccessToken -> {
                state.copy(
                    accessToken = action.accessToken,
                    refreshToken = action.refreshToken
                )
            }

            is ConnectionAction.AccessTokenRevoked -> {
                state.copy(
                    state = ConnectionState.CONNECTING,
                    accessToken = null,
                )
            }

            is ConnectionAction.MappedComponentIDs -> {
                state.copy(backendComponentIDs = action.componentIDs)
            }

            is ConnectionAction.ConnectionFailed -> {
                state.copy(
                    state = ConnectionState.LOGIN,
                    connectionError = action.error
                )
            }

            is ConnectionAction.ConnectionSucceeded -> {
                state.copy(
                    state = ConnectionState.CONNECTED,
                    backendEventIDs = action.eventIDs
                )
            }

            is ConnectionAction.EventsUpdated -> {
                state.copy(backendEventIDs = action.eventIDs)
            }

            is ConnectionAction.PublishEvent -> {
                state.copy(pendingEvents = state.pendingEvents + action.event)
            }

            is ConnectionAction.EventPublished -> {
                state.copy(pendingEvents = state.pendingEvents.filterNot { it.clientId == action.clientId })
            }
        }
    }
}
