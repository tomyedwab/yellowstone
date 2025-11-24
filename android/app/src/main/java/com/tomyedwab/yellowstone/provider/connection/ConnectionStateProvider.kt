package com.tomyedwab.yellowstone.provider.connection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

interface StateDispatcher {
    fun dispatch(action: ConnectionAction)
}

class ConnectionStateProvider : ViewModel(), StateDispatcher {
    private val _connectionState: MutableLiveData<HubConnectionState> =
        MutableLiveData(HubConnectionState.Uninitialized())
    val connectionState: LiveData<HubConnectionState> = _connectionState
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCleared() {
        super.onCleared()
        mainScope.cancel()
    }

    override fun dispatch(action: ConnectionAction) {
        // Ensure we're on the main thread when reading state, computing new state, and updating LiveData
        // This prevents race conditions where multiple actions see the same stale state
        val isMainThread = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        val threadName = Thread.currentThread().name

        Timber.d("dispatch() called on thread: $threadName (isMainThread: $isMainThread), action: ${action::class.simpleName}")

        if (isMainThread) {
            val currentState = _connectionState.value ?: HubConnectionState.Uninitialized()
            Timber.d("Dispatching ${action::class.simpleName} from state: ${currentState::class.simpleName}")
            val newState = connectionStateReducer(currentState, action)
            Timber.d("Transitioning from ${currentState::class.simpleName} to ${newState::class.simpleName}")

            if (currentState != newState) {
                _connectionState.value = newState
                Timber.d("State updated to: ${newState::class.simpleName}")
            } else {
                Timber.d("State unchanged (${currentState::class.simpleName})")
            }
        } else {
            Timber.d("Scheduling dispatch on main thread for action: ${action::class.simpleName}")
            mainScope.launch {
                val currentState = _connectionState.value ?: HubConnectionState.Uninitialized()
                Timber.d("Dispatching ${action::class.simpleName} from state: ${currentState::class.simpleName} (on main thread)")
                val newState = connectionStateReducer(currentState, action)
                Timber.d("Transitioning from ${currentState::class.simpleName} to ${newState::class.simpleName}")

                if (currentState != newState) {
                    _connectionState.value = newState
                    Timber.d("State updated to: ${newState::class.simpleName}")
                } else {
                    Timber.d("State unchanged (${currentState::class.simpleName})")
                }
            }
        }
    }

    private fun connectionStateReducer(
        state: HubConnectionState,
        action: ConnectionAction
    ): HubConnectionState {
        return when (action) {
            is ConnectionAction.AccountListLoaded -> {
                HubConnectionState.WaitingForLogin(action.accountList, action.accountList.accounts.find { account ->
                    account.id == action.accountList.selectedAccount
                })
            }

            is ConnectionAction.ConnectionSelected -> {
                val nextAccount = state.accountList.accounts.find { account ->
                    account.id == action.selectedAccount
                } ?: return state
                if (nextAccount.refreshToken != null) {
                    // Try to log in immediately
                    HubConnectionState.Connecting.RefreshingAccessToken(
                        state.accountList,
                        nextAccount,
                        null,
                        nextAccount.refreshToken
                    )
                } else {
                    // User needs to enter password
                    HubConnectionState.WaitingForLogin(state.accountList, nextAccount)
                }
            }

            is ConnectionAction.StartLogin -> {
                HubConnectionState.Connecting.LoggingIn(
                    state.accountList,
                    action.account,
                    action.password
                )
            }

            is ConnectionAction.StartConnection -> {
                when (state) {
                    is HubConnectionState.Connecting.LoggingIn -> {
                        HubConnectionState.Connecting.RefreshingAccessToken(
                            state.accountList,
                            action.account,
                            state.loginPassword,
                            action.refreshToken
                        )
                    }

                    is HubConnectionState.Connecting -> {
                        // Retain password while we are trying to connect
                        HubConnectionState.Connecting.RefreshingAccessToken(
                            state.accountList,
                            action.account,
                            state.loginPassword,
                            action.refreshToken,
                        )
                    }

                    else -> {
                        HubConnectionState.Connecting.RefreshingAccessToken(
                            state.accountList,
                            action.account,
                            null,
                            action.refreshToken
                        )
                    }
                }
            }

            is ConnectionAction.RefreshTokenInvalid -> {
                when (state) {
                    is HubConnectionState.Connecting.RefreshingAccessToken -> {
                        // If the refresh token was invalidated for some reason,
                        // we will discover it when we try to refresh the access
                        // token. In this case, fall back to login screen,
                        // clearing the invalid token and preserving the error message
                        val updatedAccounts = state.accountList.accounts.map {
                            if (it.id == state.loginAccount.id) {
                                it.copy(refreshToken = null)
                            } else {
                                it
                            }
                        }
                        val updatedLoginAccount =
                            updatedAccounts.find { it.id == state.loginAccount.id } ?: state.loginAccount

                        HubConnectionState.WaitingForLogin(
                            HubAccountList(
                                updatedAccounts,
                                state.accountList.selectedAccount,
                            ),
                            updatedLoginAccount,
                            state.loginPassword, // Preserve password for retry
                            action.error // connectionError
                        )
                    }

                    is HubConnectionState.Connecting.RegisteringAppComponents -> {
                        HubConnectionState.WaitingForLogin(
                            HubAccountList(
                                state.accountList.accounts.map {
                                    if (it.id == state.loginAccount.id) {
                                        it.copy(refreshToken = null)
                                    } else {
                                        it
                                    }
                                },
                                state.accountList.selectedAccount,
                            ),
                            state.loginAccount,
                            state.loginPassword,
                            action.error
                        )
                    }

                    is HubConnectionState.Connecting.InitialConnection -> {
                        HubConnectionState.WaitingForLogin(
                            HubAccountList(
                                state.accountList.accounts.map {
                                    if (it.id == state.loginAccount.id) {
                                        it.copy(refreshToken = null)
                                    } else {
                                        it
                                    }
                                },
                                state.accountList.selectedAccount,
                            ),
                            state.loginAccount,
                            state.loginPassword,
                            action.error
                        )
                    }

                    // There is no other case where this is expected to happen
                    else -> state
                }
            }

            is ConnectionAction.AccessTokenRevoked -> {
                when (state) {
                    is HubConnectionState.Connecting.RegisteringAppComponents -> {
                        HubConnectionState.Connecting.RefreshingAccessToken(
                            state.accountList,
                            state.loginAccount,
                            state.loginPassword,
                            state.refreshToken
                        )
                    }

                    is HubConnectionState.Connecting.InitialConnection -> {
                        HubConnectionState.Connecting.RefreshingAccessToken(
                            state.accountList,
                            state.loginAccount,
                            state.loginPassword,
                            state.refreshToken,
                            state.backendComponentIDs
                        )
                    }

                    is HubConnectionState.Connected -> {
                        HubConnectionState.Connecting.RefreshingAccessToken(
                            state.accountList,
                            state.loginAccount,
                            null, // loginPassword is not available in Connected state
                            state.refreshToken,
                            state.backendComponentIDs,
                            state.backendEventIDs
                        )
                    }

                    // Not sure how this happened, but just fall back to login screen
                    else -> HubConnectionState.WaitingForLogin(state.accountList, null)
                }
            }

            is ConnectionAction.ReceivedAccessToken -> {
                when (state) {
                    is HubConnectionState.Connecting.RefreshingAccessToken -> {
                        // If we have backendComponentIDs, we can skip registration and go directly to InitialConnection
                        if (state.backendComponentIDs != null && state.backendEventIDs != null) {
                            HubConnectionState.Connecting.InitialConnection(
                                state.accountList,
                                state.loginAccount,
                                state.loginPassword,
                                action.refreshToken,
                                action.accessToken,
                                state.backendComponentIDs
                            )
                        } else {
                            // Normal flow: go to RegisteringAppComponents
                            HubConnectionState.Connecting.RegisteringAppComponents(
                                state.accountList,
                                state.loginAccount,
                                state.loginPassword,
                                action.refreshToken,
                                action.accessToken
                            )
                        }
                    }

                    is HubConnectionState.Connected -> {
                        HubConnectionState.Connected(
                            state.accountList,
                            state.loginAccount,
                            action.refreshToken,
                            action.accessToken,
                            state.backendComponentIDs,
                            state.backendEventIDs,
                            state.pendingEvents,
                        )
                    }

                    // This event only makes sense in this one particular state
                    else -> state
                }
            }

            is ConnectionAction.MappedComponentIDs -> {
                when (state) {
                    is HubConnectionState.Connecting.RegisteringAppComponents -> {
                        HubConnectionState.Connecting.InitialConnection(
                            state.accountList,
                            state.loginAccount,
                            state.loginPassword,
                            state.refreshToken,
                            state.accessToken,
                            action.componentIDs
                        )
                    }

                    // This is an invalid transition so do nothing
                    else -> state
                }
            }

            is ConnectionAction.ConnectionFailed -> {
                when (state) {
                    is HubConnectionState.Connecting -> {
                        HubConnectionState.WaitingForLogin(
                            state.accountList,
                            state.loginAccount,
                            state.loginPassword,
                            action.error
                        )
                    }

                    is HubConnectionState.Connected -> {
                        HubConnectionState.WaitingForLogin(
                            state.accountList,
                            state.loginAccount,
                            null,
                            action.error
                        )
                    }

                    // If we're not connecting or connected, then the connection can't fail
                    else -> state
                }
            }

            is ConnectionAction.ConnectionSucceeded -> {
                when (state) {
                    is HubConnectionState.Connecting.InitialConnection -> {
                        HubConnectionState.Connected(
                            state.accountList,
                            state.loginAccount,
                            state.refreshToken,
                            state.accessToken,
                            state.backendComponentIDs,
                            action.eventIDs,
                            emptyList() // pendingEvents
                        )
                    }

                    // This event only makes sense following InitialConnection
                    else -> state
                }
            }

            is ConnectionAction.EventsUpdated -> {
                when (state) {
                    is HubConnectionState.Connected -> {
                        HubConnectionState.Connected(
                            state.accountList,
                            state.loginAccount,
                            state.refreshToken,
                            state.accessToken,
                            state.backendComponentIDs,
                            action.eventIDs,
                            state.pendingEvents,
                        )
                    }

                    else -> state
                }
            }

            is ConnectionAction.PublishEvent -> {
                when (state) {
                    is HubConnectionState.Connected -> {
                        HubConnectionState.Connected(
                            state.accountList,
                            state.loginAccount,
                            state.refreshToken,
                            state.accessToken,
                            state.backendComponentIDs,
                            state.backendEventIDs,
                            state.pendingEvents + action.event,
                        )
                    }

                    else -> state
                }
            }

            is ConnectionAction.EventPublished -> {
                when (state) {
                    is HubConnectionState.Connected -> {
                        HubConnectionState.Connected(
                            state.accountList,
                            state.loginAccount,
                            state.refreshToken,
                            state.accessToken,
                            state.backendComponentIDs,
                            state.backendEventIDs,
                            state.pendingEvents.filterNot { it.clientId == action.clientId },
                        )
                    }

                    else -> state
                }
            }
        }
    }
}
