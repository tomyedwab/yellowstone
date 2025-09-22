package com.tomyedwab.yellowstone.services.connection

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.provider.connection.*
import com.tomyedwab.yellowstone.utils.BinaryReader
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class ComponentAsset(val binaryAssetName: String, val md5AssetName: String)

class ConnectionService : Service(), ViewModelStoreOwner {

    private val binder = ConnectionBinder()
    private lateinit var connectionStateProvider: ConnectionStateProvider
    // WARNING: Enable insecure connections for development/self-hosted environments only
    // In production, set this to false and use proper SSL certificates
    private val authService = AuthService(allowInsecureConnections = true)
    private val dataViewService = DataViewService(authService)
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    // Component asset map - will be initialized via initializeComponentAssets()
    private lateinit var componentAssetMap: Map<String, ComponentAsset>

    // Component hashes loaded from assets
    private var componentHashes = ConcurrentHashMap<String, String>()

    // Binary reader for lazy loading
    private lateinit var binaryReader: BinaryReader

    // Coroutine scope for background operations
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track current state to avoid redundant operations
    private var lastProcessedState: HubConnectionState? = null

    // Track polling state to prevent multiple polling threads
    private var isPolling = false

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    inner class ConnectionBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        connectionStateProvider = ConnectionStateProvider()
        binaryReader = BinaryReader(this)

        // Start observing connection state changes
        connectionStateProvider.connectionState.observeForever { state -> handleStateChange(state) }

        // Component hashes will be loaded when initializeComponentAssets() is called
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _viewModelStore.clear()
    }

    fun getConnectionStateProvider(): ConnectionStateProvider = connectionStateProvider
    fun getAuthService(): AuthService = authService
    fun getDataViewService(): DataViewService = dataViewService

    /**
     * Initialize the component asset map. Must be called after service binding.
     * @param componentAssets Map of component names to ComponentAsset instances
     */
    fun initializeComponentAssets(componentAssets: Map<String, ComponentAsset>) {
        componentAssetMap = componentAssets
        // Reload component hashes with the new asset map
        loadComponentHashes()
    }

    private fun loadComponentHashes() {
        serviceScope.launch {
            if (!::componentAssetMap.isInitialized) {
                Log.w("ConnectionService", "Component asset map not initialized yet")
                return@launch
            }

            componentAssetMap.forEach { (componentName, assetInfo) ->
                try {
                    // Read MD5 hash from assets
                    val md5Hash =
                            assets.open(assetInfo.md5AssetName).use { inputStream ->
                                inputStream.bufferedReader().readText().trim()
                            }
                    componentHashes[componentName] = md5Hash
                    Log.i("ConnectionService", "Loaded hash for $componentName: $md5Hash")
                } catch (e: Exception) {
                    Log.e("ConnectionService", "Failed to load hash for $componentName", e)
                }
            }
            Log.i("ConnectionService", "Component hashes loaded: ${componentHashes.keys}")
        }
    }

    /**
     * Lazily loads a component binary when needed
     * @param componentName The name of the component to load
     * @return ByteArray containing the binary data, or null if not found
     */
    suspend fun loadComponentBinary(componentName: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                if (!::componentAssetMap.isInitialized) {
                    Log.w("ConnectionService", "Component asset map not initialized yet")
                    return@withContext null
                }

                val assetInfo = componentAssetMap[componentName]
                if (assetInfo != null) {
                    val binaryData =
                            assets.open(assetInfo.binaryAssetName).use { inputStream ->
                                inputStream.readBytes()
                            }
                    Log.i(
                            "ConnectionService",
                            "Loaded binary for $componentName: ${binaryData.size} bytes"
                    )
                    binaryData
                } else {
                    Log.w(
                            "ConnectionService",
                            "No asset mapping found for component: $componentName"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e("ConnectionService", "Failed to load binary for $componentName", e)
                null
            }
        }
    }

    private fun handleStateChange(state: HubConnectionState) {
        // Avoid processing the same state multiple times
        if (lastProcessedState == state) return
        lastProcessedState = state

        when (state.state) {
            ConnectionState.CONNECTING -> handleConnectingState(state)
            ConnectionState.CONNECTED -> handleConnectedState(state)
            else -> {
                /* No action needed for other states */
            }
        }
    }

    private fun handleConnectingState(state: HubConnectionState) {
        serviceScope.launch {
            try {
                when {
                    // Case 1: Ready to wait for event sync
                    state.loginAccount != null &&
                            state.refreshToken != null &&
                            state.accessToken != null &&
                            state.backendComponentIDs != null -> {
                        waitForEventSync(
                                state.loginAccount.url,
                                state.refreshToken,
                                state.accessToken,
                                state.backendComponentIDs
                        )
                    }

                    // Case 2: Ready to register app components
                    state.loginAccount != null &&
                            state.refreshToken != null &&
                            state.accessToken != null -> {
                        sendAppRegistrationRequest(
                                state.loginAccount.url,
                                state.refreshToken,
                                state.accessToken
                        )
                    }

                    // Case 3: Need to get access token
                    state.loginAccount != null && state.refreshToken != null -> {
                        sendAccessTokenRequest(state.loginAccount.url, state.refreshToken)
                    }

                    // Case 4: Need to login with username/password
                    state.loginAccount != null &&
                            state.loginPassword != null &&
                            state.refreshToken == null -> {
                        sendLoginRequest(
                                state.loginAccount.url,
                                state.loginAccount.id, // Using id as username
                                state.loginPassword,
                                state.loginAccount
                        )
                    }
                }
            } catch (e: Exception) {
                connectionStateProvider.dispatch(
                        ConnectionAction.ConnectionFailed("Connection error: ${e.message}")
                )
            }
        }
    }

    private fun handleConnectedState(state: HubConnectionState) {
        // Start polling for changes when connected
        if (state.loginAccount != null &&
                        state.refreshToken != null &&
                        state.accessToken != null &&
                        state.backendEventIDs != null
        ) {

            // Publish any pending events in parallel with polling
            if (state.pendingEvents.isNotEmpty()) {
                serviceScope.launch {
                    publishPendingEvents(
                            state.loginAccount.url,
                            state.refreshToken,
                            state.accessToken,
                            state.pendingEvents
                    )
                }
            }

            // Only start polling if not already polling
            if (!isPolling) {
                isPolling = true
                serviceScope.launch {
                    try {
                        pollForChanges(
                                state.loginAccount.url,
                                state.refreshToken,
                                state.accessToken,
                                state.backendEventIDs
                        )
                    } finally {
                        isPolling = false
                    }
                }
            }
        }
    }

    private suspend fun publishPendingEvents(
            url: String,
            refreshToken: String,
            accessToken: String,
            pendingEvents: List<PendingEvent>
    ) {
        try {
            for (event in pendingEvents) {
                val eventJson = gson.toJson(event)
                val requestBody = eventJson.toRequestBody(jsonMediaType)

                val response =
                        authService.fetchAuthenticated(
                                "$url/events/publish",
                                refreshToken,
                                accessToken,
                                "POST",
                                requestBody
                        )

                response.use {
                    if (it.isSuccessful) {
                        Log.i(
                                "ConnectionService",
                                "Successfully published event: ${event.clientId}"
                        )
                        connectionStateProvider.dispatch(
                                ConnectionAction.EventPublished(event.clientId)
                        )
                    } else {
                        Log.e(
                                "ConnectionService",
                                "Failed to publish event ${event.clientId}: ${it.code} - ${it.body?.string()}"
                        )
                        throw Exception("Event publishing failed: ${it.code}")
                    }
                }
            }
        } catch (e: UnauthenticatedError) {
            connectionStateProvider.dispatch(
                    ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
            )
        } catch (e: Exception) {
            Log.e("ConnectionService", "Error publishing events: ${e.message}", e)
            // Continue with polling even if event publishing fails
        }
    }

    private suspend fun sendLoginRequest(
            url: String,
            username: String,
            password: String,
            account: HubAccount
    ) {
        try {
            val refreshToken = authService.doLogin(url, username, password)
            connectionStateProvider.dispatch(
                    ConnectionAction.StartConnection(account, refreshToken)
            )
        } catch (e: Exception) {
            connectionStateProvider.dispatch(
                    ConnectionAction.ConnectionFailed("Login failed: ${e.message}")
            )
        }
    }

    private suspend fun sendAccessTokenRequest(url: String, refreshToken: String) {
        try {
            val (accessToken, newRefreshToken) = authService.refreshAccessToken(url, refreshToken)
            connectionStateProvider.dispatch(
                    ConnectionAction.ReceivedAccessToken(accessToken, newRefreshToken)
            )
        } catch (e: Exception) {
            connectionStateProvider.dispatch(
                    ConnectionAction.ConnectionFailed("Token refresh failed: ${e.message}")
            )
        }
    }

    private suspend fun sendAppRegistrationRequest(
            url: String,
            refreshToken: String,
            accessToken: String
    ) {
        try {
            if (componentHashes.isEmpty()) {
                throw Exception("Missing component hashes")
            }

            val hashesJson = gson.toJson(componentHashes)
            val requestBody = hashesJson.toRequestBody(jsonMediaType)

            val response =
                    authService.fetchAuthenticated(
                            "$url/apps/register",
                            refreshToken,
                            accessToken,
                            "POST",
                            requestBody
                    )

            response.use {
                if (it.isSuccessful) {
                    val responseJson =
                            gson.fromJson(
                                    it.body?.string(),
                                    object : TypeToken<Map<String, String?>>() {}.type
                            ) as
                                    Map<String, String?>

                    val componentIDs = mutableMapOf<String, String>()

                    // Handle components that need installation
                    for ((appName, instanceId) in responseJson) {
                        if (instanceId != null) {
                            componentIDs[appName] = instanceId
                        } else {
                            // Component needs to be installed - send binary to server
                            Log.i("ConnectionService", "Installing component: $appName")
                            val installedInstanceId =
                                    componentHashes[appName]?.let { it1 ->
                                        installComponent(
                                                url,
                                                refreshToken,
                                                accessToken,
                                                appName,
                                                it1
                                        )
                                    }
                            if (installedInstanceId != null) {
                                componentIDs[appName] = installedInstanceId
                                Log.i(
                                        "ConnectionService",
                                        "Successfully installed $appName with instance ID: $installedInstanceId"
                                )
                            } else {
                                throw Exception("Failed to install component: $appName")
                            }
                        }
                    }

                    connectionStateProvider.dispatch(
                            ConnectionAction.MappedComponentIDs(BackendComponentIDs(componentIDs))
                    )
                } else {
                    throw Exception("App registration failed: ${it.code}")
                }
            }
        } catch (e: UnauthenticatedError) {
            connectionStateProvider.dispatch(
                    ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
            )
        } catch (e: Exception) {
            connectionStateProvider.dispatch(
                    ConnectionAction.ConnectionFailed("App registration failed: ${e.message}")
            )
        }
    }

    /**
     * Installs a component by uploading its binary to the server
     * @param url The server base URL
     * @param refreshToken The refresh token for authentication
     * @param accessToken The access token for authentication
     * @param componentName The name of the component to install
     * @return The instance ID returned by the server, or null if installation failed
     */
    private suspend fun installComponent(
            url: String,
            refreshToken: String,
            accessToken: String,
            componentName: String,
            componentHash: String
    ): String? {
        return try {
            // Load the binary for this component
            val binaryData = loadComponentBinary(componentName)
            if (binaryData == null) {
                Log.e("ConnectionService", "No binary found for component: $componentName")
                return null
            }

            // Create multipart request with the binary
            val binaryRequestBody =
                    binaryData.toRequestBody("application/octet-stream".toMediaType())
            val multipartBody =
                    MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("content", "$componentName-binary", binaryRequestBody)
                            .addFormDataPart("hash", componentHash)
                            .build()

            Log.i(
                    "ConnectionService",
                    "Uploading binary for $componentName (${binaryData.size} bytes)"
            )

            val response =
                    authService.fetchAuthenticated(
                            "$url/apps/install",
                            refreshToken,
                            accessToken,
                            "POST",
                            multipartBody
                    )

            response.use {
                if (it.isSuccessful) {
                    val responseBody = it.body?.string()
                    val responseJson = gson.fromJson(responseBody, JsonObject::class.java)
                    val instanceId = responseJson.get("instanceId")?.asString

                    if (instanceId != null) {
                        Log.i(
                                "ConnectionService",
                                "Component $componentName installed with instance ID: $instanceId"
                        )
                        instanceId
                    } else {
                        Log.e(
                                "ConnectionService",
                                "Server did not return instance_id for $componentName"
                        )
                        null
                    }
                } else {
                    Log.e(
                            "ConnectionService",
                            "Component installation failed for $componentName: ${it.code} ${it.message}"
                    )
                    null
                }
            }
        } catch (e: UnauthenticatedError) {
            connectionStateProvider.dispatch(
                    ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
            )
            null
        } catch (e: Exception) {
            Log.e(
                    "ConnectionService",
                    "Exception during component installation for $componentName",
                    e
            )
            null
        }
    }

    private suspend fun waitForEventSync(
            url: String,
            refreshToken: String,
            accessToken: String,
            backendComponentIDs: BackendComponentIDs
    ) {
        try {
            val initialEventIds = mutableMapOf<String, Int>()
            backendComponentIDs.componentMap.values.forEach { instanceId ->
                initialEventIds[instanceId] = -1
            }

            while (true) {
                val eventsJson = gson.toJson(initialEventIds)
                val requestBody = eventsJson.toRequestBody(jsonMediaType)

                val response =
                        authService.fetchAuthenticated(
                                "$url/events/poll",
                                refreshToken,
                                accessToken,
                                "POST",
                                requestBody
                        )

                response.use {
                    if (it.isSuccessful) {
                        val data =
                                gson.fromJson(
                                        it.body?.string(),
                                        object : TypeToken<Map<String, Int>>() {}.type
                                ) as
                                        Map<String, Int>

                        val foundUninitialized = data.values.any { eventId -> eventId == -1 }
                        if (!foundUninitialized) {
                            connectionStateProvider.dispatch(
                                    ConnectionAction.ConnectionSucceeded(data)
                            )
                            return
                        }
                    }
                }

                delay(1000) // Wait 1 second before next poll
            }
        } catch (e: UnauthenticatedError) {
            connectionStateProvider.dispatch(
                    ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
            )
        } catch (e: Exception) {
            connectionStateProvider.dispatch(
                    ConnectionAction.ConnectionFailed("Event sync failed: ${e.message}")
            )
        }
    }

    private suspend fun pollForChanges(
            url: String,
            refreshToken: String,
            accessToken: String,
            initialEventIDs: Map<String, Int>
    ) {
        var currentEventIDs = initialEventIDs

        while (true) {
            try {
                delay(1000) // Wait 1 second before polling

                val eventsJson = gson.toJson(currentEventIDs)
                val requestBody = eventsJson.toRequestBody(jsonMediaType)

                val response =
                        authService.fetchAuthenticated(
                                "$url/events/poll",
                                refreshToken,
                                accessToken,
                                "POST",
                                requestBody
                        )

                response.use {
                    when (it.code) {
                        200 -> {
                            val data =
                                    gson.fromJson(
                                            it.body?.string(),
                                            object : TypeToken<Map<String, Int>>() {}.type
                                    ) as
                                            Map<String, Int>
                            currentEventIDs = data
                            connectionStateProvider.dispatch(ConnectionAction.EventsUpdated(data))
                        }
                        304 -> {
                            // Not modified - dispatch the same event IDs
                            connectionStateProvider.dispatch(
                                    ConnectionAction.EventsUpdated(currentEventIDs)
                            )
                        }
                        else -> {
                            throw Exception("HTTP ${it.code}: ${it.message}")
                        }
                    }
                }
            } catch (e: UnauthenticatedError) {
                connectionStateProvider.dispatch(
                        ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
                )
            } catch (e: Exception) {
                // In production, this should transition to "reconnecting" state
                connectionStateProvider.dispatch(
                        ConnectionAction.ConnectionFailed("Polling failed: ${e.message}")
                )
                break // Exit the polling loop on error
            }
        }
    }
}
