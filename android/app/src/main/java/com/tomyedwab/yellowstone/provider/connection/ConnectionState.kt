package com.tomyedwab.yellowstone.provider.connection

enum class ConnectionState {
    NOT_INITIALIZED,
    NO_SELECTION,
    LOGIN,
    CONNECTING,
    CONNECTED
}

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

data class HubConnectionState(
    val state: ConnectionState = ConnectionState.NOT_INITIALIZED,
    val accountList: HubAccountList? = null,
    val loginAccount: HubAccount? = null,
    val loginPassword: String? = null,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val backendComponentIDs: BackendComponentIDs? = null,
    val backendEventIDs: Map<String, Int>? = null,
    val connectionError: String? = null,
    val pendingEvents: List<PendingEvent> = emptyList()
)