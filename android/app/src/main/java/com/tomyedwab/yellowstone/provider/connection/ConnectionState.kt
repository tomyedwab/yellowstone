package com.tomyedwab.yellowstone.provider.connection

data class HubAccount(
    val id: String,
    val name: String,
    val url: String,
    val refreshToken: String? = null
)

data class HubAccountList(
    val accounts: List<HubAccount>,
    val selectedAccount: String? = null
)

data class BackendComponentIDs(
    val componentMap: Map<String, String>
)

data class PendingEvent(
    val clientId: String,
    val type: String,
    val timestamp: String,
    val data: Map<String, Any?>
)

sealed class HubConnectionState(val accountList: HubAccountList) {
    // We haven't loaded the account list yet
    class Uninitialized() : HubConnectionState(HubAccountList(emptyList(), null))

    // We are waiting for the user to select an account or log in
    class WaitingForLogin(
        accountList: HubAccountList,
        val loginAccount: HubAccount? = null,
        val loginPassword: String? = null,
        val connectionError: String? = null
    ) : HubConnectionState(accountList)

    // Shared class for all the connecting-related states below
    sealed class Connecting(
        accountList: HubAccountList,
        val loginAccount: HubAccount,
        val loginPassword: String?) : HubConnectionState(accountList) {

        // We are passing account credentials to the hub and waiting for a refresh token
        class LoggingIn(
            accountList: HubAccountList,
            loginAccount: HubAccount,
            loginPassword: String?
        ) : Connecting(accountList, loginAccount, loginPassword)

        // We have a refresh token and are waiting for the access token
        class RefreshingAccessToken(
            accountList: HubAccountList,
            loginAccount: HubAccount,
            loginPassword: String?,
            val refreshToken: String,
            val backendComponentIDs: BackendComponentIDs? = null,
            val backendEventIDs: Map<String, Int>? = null
        ) : Connecting(accountList, loginAccount, loginPassword)

        // We need to check that all the components are registered
        class RegisteringAppComponents(
            accountList: HubAccountList,
            loginAccount: HubAccount,
            loginPassword: String?,
            val refreshToken: String,
            val accessToken: String
        ) : Connecting(accountList, loginAccount, loginPassword)

        // Components are registered and we are waiting for initial polling to
        // succeed
        class InitialConnection(
            accountList: HubAccountList,
            loginAccount: HubAccount,
            loginPassword: String?,
            val refreshToken: String,
            val accessToken: String,
            val backendComponentIDs: BackendComponentIDs,
        ) : Connecting(accountList, loginAccount, loginPassword)
    }

    // We are connected to the backend, all components have been installed, and
    // are ready to query data and send events
    class Connected(
        accountList: HubAccountList,
        val loginAccount: HubAccount,
        val refreshToken: String,
        val accessToken: String,
        val backendComponentIDs: BackendComponentIDs,
        val backendEventIDs: Map<String, Int>,
        val pendingEvents: List<PendingEvent> = emptyList()
    ) : HubConnectionState(accountList)
}
