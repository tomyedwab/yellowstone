package com.tomyedwab.yellowstone.provider.connection

sealed class ConnectionAction {
    data class AccountListLoaded(val accountList: HubAccountList) : ConnectionAction()
    data class ConnectionSelected(val selectedAccount: String) : ConnectionAction()
    data class StartLogin(val account: HubAccount, val password: String) : ConnectionAction()
    data class StartConnection(val account: HubAccount, val refreshToken: String) : ConnectionAction()
    data class ReceivedAccessToken(val accessToken: String, val refreshToken: String) : ConnectionAction()
    data class RefreshTokenInvalid(val error: String) : ConnectionAction()
    data class AccessTokenRevoked(val message: String?) : ConnectionAction()
    data class MappedComponentIDs(val componentIDs: BackendComponentIDs) : ConnectionAction()
    data class ConnectionFailed(val error: String) : ConnectionAction()
    data class ConnectionSucceeded(val eventIDs: Map<String, Int>) : ConnectionAction()
    data class EventsUpdated(val eventIDs: Map<String, Int>) : ConnectionAction()
    data class PublishEvent(val event: PendingEvent) : ConnectionAction()
    data class EventPublished(val clientId: String) : ConnectionAction()
}
