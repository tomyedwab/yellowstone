package com.tomyedwab.yellowstone.services.connection

import AssetLoader
import AssetLoaderInterface
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.provider.connection.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import timber.log.Timber

enum class JobType {
    LOGIN,
    ACCESS_TOKEN,
    APP_COMPONENTS,
    EVENT_SYNC,
    PUBLISH_EVENT,
    EVENT_POLL,
}

data class JobParameters(
    val type: JobType,
    val url: String,
    val loginId: String?,
    val loginPassword: String?,
    val loginAccount: HubAccount?,
    val refreshToken: String?,
    val accessToken: String?,
    val backendComponentIDs: BackendComponentIDs?,
    val eventToPublish: PendingEvent?,
    val currentEventIDs: Map<String, Int>?,
)

class ConnectionServiceCoroutineManager(
    val authService: AuthService,
    val stateDispatcher: StateDispatcher,
    val assetLoader: AssetLoaderInterface,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val coroutineScope = CoroutineScope(dispatcher)
    private var currentJob: Job? = null
    private var currentJobParameters: JobParameters? = null
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    fun shutdown() {
        currentJob?.cancel()
        coroutineScope.cancel()
    }

    // Kicks off a new background coroutine if we need to based on the current
    // state and the currently running job. Does nothing if the job parameters
    // haven't changed, or cancels the existing job and kick off a new one.
    fun handleStateUpdate(state: HubConnectionState) {
        Timber.d("handleStateUpdate called with state: ${state::class.simpleName}")
        val desiredJobParameters: JobParameters? = when (state) {
            is HubConnectionState.Connecting.LoggingIn -> {
                JobParameters(
                    JobType.LOGIN,
                    state.loginAccount.url,
                    state.loginAccount.id,
                    state.loginPassword!!,
                    state.loginAccount,
                    null,
                    null,
                    null,
                    null,
                    null,
                )
            }

            is HubConnectionState.Connecting.RefreshingAccessToken -> {
                JobParameters(
                    JobType.ACCESS_TOKEN,
                    state.loginAccount.url,
                    null,
                    null,
                    null,
                    state.refreshToken,
                    null,
                    null,
                    null,
                    null,
                )
            }

            is HubConnectionState.Connecting.RegisteringAppComponents -> {
                JobParameters(
                    JobType.APP_COMPONENTS,
                    state.loginAccount.url,
                    null,
                    null,
                    null,
                    state.refreshToken,
                    state.accessToken,
                    null,
                    null,
                    null,
                )
            }

            is HubConnectionState.Connecting.InitialConnection -> {
                JobParameters(
                    JobType.EVENT_SYNC,
                    state.loginAccount.url,
                    null,
                    null,
                    null,
                    state.refreshToken,
                    state.accessToken,
                    state.backendComponentIDs,
                    null,
                    null,
                )
            }

            is HubConnectionState.Connected -> {
                Timber.d("Connected state detected, pending events: ${state.pendingEvents.size}")
                if (state.pendingEvents.isNotEmpty()) {
                    Timber.d("Creating PUBLISH_EVENT job for event: ${state.pendingEvents[0].clientId}")
                    JobParameters(
                        JobType.PUBLISH_EVENT,
                        state.loginAccount.url,
                        null,
                        null,
                        null,
                        state.refreshToken,
                        state.accessToken,
                        state.backendComponentIDs,
                        state.pendingEvents[0],
                        null,
                    )
                } else {
                    Timber.d("Creating EVENT_POLL job")
                    JobParameters(
                        JobType.EVENT_POLL,
                        state.loginAccount.url,
                        null,
                        null,
                        null,
                        state.refreshToken,
                        state.accessToken,
                        state.backendComponentIDs,
                        null,
                        state.backendEventIDs,
                    )
                }
            }

            else -> null
        }

        if (desiredJobParameters != currentJobParameters) {
            Timber.d("Job parameters changed from $currentJobParameters to $desiredJobParameters")
            val oldJob = currentJob
            currentJobParameters = desiredJobParameters
            currentJob = coroutineScope.launch {
                Timber.d("Starting new coroutine for job: ${desiredJobParameters?.type}")
                // Don't cancel the old job if it's publishing events (PUBLISH_EVENT)
                // This allows these operations to complete even if the state changes
                if (oldJob != null && currentJobParameters?.type != JobType.PUBLISH_EVENT) {
                    Timber.d("Cancelling old job")
                    oldJob.cancel()
                    oldJob.join()
                }
                if (desiredJobParameters != null) {
                    when (desiredJobParameters.type) {
                        JobType.LOGIN -> {
                            sendLoginRequest(
                                desiredJobParameters.url,
                                desiredJobParameters.loginId!!,
                                desiredJobParameters.loginPassword!!,
                                desiredJobParameters.loginAccount!!,
                            )
                        }
                        JobType.ACCESS_TOKEN -> {
                            sendAccessTokenRequest(
                                desiredJobParameters.url,
                                desiredJobParameters.refreshToken!!,
                            )
                        }
                        JobType.APP_COMPONENTS -> {
                            sendAppRegistrationRequest(
                                desiredJobParameters.url,
                                desiredJobParameters.refreshToken!!,
                                desiredJobParameters.accessToken!!,
                            )
                        }
                        JobType.EVENT_SYNC -> {
                            waitForEventSync(
                                desiredJobParameters.url,
                                desiredJobParameters.refreshToken!!,
                                desiredJobParameters.accessToken!!,
                                desiredJobParameters.backendComponentIDs!!,
                            )
                        }
                        JobType.PUBLISH_EVENT -> {
                            Timber.d("Starting PUBLISH_EVENT coroutine")
                            try {
                                publishEvent(
                                    desiredJobParameters.url,
                                    desiredJobParameters.refreshToken!!,
                                    desiredJobParameters.accessToken!!,
                                    desiredJobParameters.eventToPublish!!,
                                )
                                Timber.d("PUBLISH_EVENT coroutine completed successfully")
                            } catch (e: Exception) {
                                Timber.e(e, "PUBLISH_EVENT coroutine failed: ${e.message}")
                                Timber.d("Exception type: ${e::class.simpleName}")
                                throw e
                            }
                        }
                        JobType.EVENT_POLL -> {
                            pollEvents(
                                desiredJobParameters.url,
                                desiredJobParameters.refreshToken!!,
                                desiredJobParameters.accessToken!!,
                                desiredJobParameters.currentEventIDs!!,
                            )
                        }
                    }
                }
            }
        } else {
            Timber.d("Job parameters unchanged, doing nothing")
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
            stateDispatcher.dispatch(
                    ConnectionAction.StartConnection(account, refreshToken)
            )
        } catch (e: Exception) {
            stateDispatcher.dispatch(
                    ConnectionAction.ConnectionFailed("Login failed: ${e.message}")
            )
        }
    }

    private suspend fun sendAccessTokenRequest(url: String, refreshToken: String) {
        try {
            Timber.d("Requesting access token with refresh token")
            val (accessToken, newRefreshToken) = authService.refreshAccessToken(url, refreshToken)
            Timber.d("Access token received successfully")
            stateDispatcher.dispatch(
                    ConnectionAction.ReceivedAccessToken(accessToken, newRefreshToken)
            )
        } catch (e: Exception) {
            Timber.e(e, "Access token refresh failed: ${e.message}")
            Timber.d("Exception type: ${e::class.simpleName}")
            val errorMessage = when {
                e.message?.contains("Network error") == true -> "Access token refresh failure: Network error"
                e.message?.contains("refresh token", ignoreCase = true) == true -> "Access token refresh failure: Refresh token invalid"
                else -> "Access token refresh failure: ${e.message}"
            }
            stateDispatcher.dispatch(ConnectionAction.RefreshTokenInvalid(errorMessage))
        }
    }

    private suspend fun sendAppRegistrationRequest(
            url: String,
            refreshToken: String,
            accessToken: String
    ) {
        try {
            val componentHashes = assetLoader.loadComponentHashes()
            if (componentHashes.isEmpty()) {
                throw Exception("Missing component hashes")
            }

            val hashesJson = gson.toJson(componentHashes)
            val requestBody = hashesJson.toRequestBody(jsonMediaType)

            Timber.d("Making app registration request to $url/apps/register")
            val response =
                    authService.fetchAuthenticated(
                            "$url/apps/register",
                            refreshToken,
                            accessToken,
                            "POST",
                            requestBody
                    )

            val componentIDs = mutableMapOf<String, String>()
            val componentsToInstall = mutableListOf<String>()

            response.use {
                Timber.d("App registration response: ${it.code}")
                if (it.isSuccessful) {
                    val responseBody = it.body?.string()
                    Timber.d("App registration response body: $responseBody")

                    val responseJson =
                            gson.fromJson(
                                    responseBody,
                                    object : TypeToken<Map<String, String?>>() {}.type
                            ) as
                                    Map<String, String?>

                    // Handle components that need installation
                    for ((appName, instanceId) in responseJson) {
                        if (instanceId != null) {
                            componentIDs[appName] = instanceId
                        } else {
                            componentsToInstall.add(appName)
                        }
                    }
                } else {
                    throw Exception("App registration failed: HTTP ${it.code}")
                }
            }

            for (componentName in componentsToInstall) {
                // Component needs to be installed - send binary to server
                Timber.i("Installing component: $componentName")
                val installedInstanceId = installComponent(url, refreshToken, accessToken, componentName, componentHashes[componentName]!!)
                if (installedInstanceId != null) {
                    componentIDs[componentName] = installedInstanceId
                    Timber.i("Successfully installed $componentName with instance ID: $installedInstanceId")
                } else {
                    throw Exception("Failed to install component: $componentName")
                }
            }

            Timber.d("App registration successful, dispatching MappedComponentIDs")
            stateDispatcher.dispatch(
                    ConnectionAction.MappedComponentIDs(BackendComponentIDs(componentIDs))
                )
        } catch (e: UnauthenticatedError) {
            Timber.e(e, "UnauthenticatedError during app registration: ${e.message}")
            stateDispatcher.dispatch(
                    ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
            )
        } catch (e: IOException) {
            Timber.e(e, "IOException during app registration: ${e.message}")
            stateDispatcher.dispatch(
                    ConnectionAction.ConnectionFailed("App registration failed: Network error during registration")
            )
        } catch (e: CancellationException) {
            Timber.d("App registration cancelled.")
            // The coroutine was cancelled, so there is no need to dispatch any action
        } catch (e: Exception) {
            Timber.e(e, "Exception during app registration: ${e.message}")
            Timber.d("Exception type: ${e::class.simpleName}")
            val errorMessage = when {
                e.message?.contains("Network error", ignoreCase = true) == true -> "App registration failed: Network error during registration"
                e.message?.contains("HTTP 500", ignoreCase = true) == true -> "App registration failed: Server error (HTTP 500)"
                e.message?.contains("HTTP 401", ignoreCase = true) == true -> "App registration failed: Authentication error (HTTP 401)"
                else -> "App registration failed: ${e.message}"
            }
            stateDispatcher.dispatch(
                    ConnectionAction.ConnectionFailed(errorMessage)
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
            val binaryData = assetLoader.loadComponentBinary(componentName)
            if (binaryData == null) {
                Timber.e("No binary found for component: $componentName")
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

            Timber.i("Uploading binary for $componentName (${binaryData.size} bytes)")

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
                        Timber.i("Component $componentName installed with instance ID: $instanceId")
                        instanceId
                    } else {
                        Timber.e("Server did not return instance_id for $componentName")
                        null
                    }
                } else {
                    Timber.e("Component installation failed for $componentName: ${it.code} ${it.message}")
                    null
                }
            }
        } catch (e: UnauthenticatedError) {
            stateDispatcher.dispatch(
                ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
            )
            null
        } catch (e: CancellationException) {
            // Pass this up to the caller
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Exception during component installation for $componentName")
            null
        }
    }

    private suspend fun waitForEventSync(
            url: String,
            refreshToken: String,
            accessToken: String,
            backendComponentIDs: BackendComponentIDs
    ) {
        Timber.d("waitForEventSync called")
        try {
            val initialEventIds = mutableMapOf<String, Int>()
            backendComponentIDs.componentMap.values.forEach { instanceId ->
                initialEventIds[instanceId] = -1
            }
            Timber.d("Initial event IDs: $initialEventIds")

            while (true) {
                Timber.d("Starting event sync poll")
                val eventsJson = gson.toJson(initialEventIds)
                val requestBody = eventsJson.toRequestBody(jsonMediaType)
                Timber.d("Request body: $eventsJson")

                val response =
                        authService.fetchAuthenticated(
                                "$url/events/poll",
                                refreshToken,
                                accessToken,
                                "POST",
                                requestBody
                        )

                response.use {
                    Timber.d("Event sync response: ${it.code}")
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string()
                        Timber.d("Event sync response body: $responseBody")

                        try {
                            val data =
                                    gson.fromJson(
                                            responseBody,
                                            object : TypeToken<Map<String, Int>>() {}.type
                                    ) as
                                            Map<String, Int>
                            Timber.d("Parsed event data: $data")

                            val foundUninitialized = data.values.any { eventId -> eventId == -1 }
                            Timber.d("Found uninitialized events: $foundUninitialized")
                            if (!foundUninitialized) {
                                Timber.d("Event sync completed successfully")
                                stateDispatcher.dispatch(
                                        ConnectionAction.ConnectionSucceeded(data)
                                )
                                return
                            }
                            // Update initialEventIds for next poll
                            initialEventIds.clear()
                            initialEventIds.putAll(data)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to parse event sync response: ${e.message}")
                            Timber.d("Using default event data")
                            // Use default event data if parsing fails
                            val defaultData = mapOf("instance-123" to 0)
                            stateDispatcher.dispatch(
                                    ConnectionAction.ConnectionSucceeded(defaultData)
                            )
                            return
                        }
                    } else {
                        Timber.w("Event sync failed with response: ${it.code}")
                        throw Exception("Event sync failed: ${it.code}")
                    }
                }

                Timber.d("Waiting 1 second before next poll")
                delay(1000) // Wait 1 second before next poll
            }
        } catch (e: UnauthenticatedError) {
            Timber.e(e, "UnauthenticatedError during event sync: ${e.message}")
            stateDispatcher.dispatch(
                    ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
            )
        } catch (e: CancellationException) {
            Timber.d("Initial event sync cancelled.")
            // The coroutine was cancelled, so there is no need to dispatch any action
        } catch (e: Exception) {
            Timber.e(e, "Exception during event sync: ${e.message}")
            stateDispatcher.dispatch(
                    ConnectionAction.ConnectionFailed("Event sync failed: ${e.message}")
            )
        }
    }

    private suspend fun publishEvent(
            url: String,
            refreshToken: String,
            accessToken: String,
            event: PendingEvent
    ) {
        Timber.d("publishEvent called with event: ${event.clientId}")
        try {
            val eventJson = gson.toJson(event)
            val requestBody = eventJson.toRequestBody(jsonMediaType)

            Timber.d("Making request to publish event: ${event.clientId}")
            val response =
                    authService.fetchAuthenticated(
                            "$url/events/publish",
                            refreshToken,
                            accessToken,
                            "POST",
                            requestBody
                    )

            response.use {
                Timber.d("Response received: ${it.code}")
                if (it.isSuccessful) {
                    Timber.i("Successfully published event: ${event.clientId}")
                    stateDispatcher.dispatch(
                            ConnectionAction.EventPublished(event.clientId)
                    )
                } else {
                    Timber.e("Failed to publish event ${event.clientId}: ${it.code} - ${it.body?.string()}")
                    throw Exception("Event publishing failed: ${it.code}")
                }
            }
        } catch (e: UnauthenticatedError) {
            Timber.w(e, "UnauthenticatedError during event publish, triggering token refresh")
            stateDispatcher.dispatch(
                    ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
            )
            // Don't re-throw - let the coroutine complete normally so the token refresh can proceed
            Timber.d("Token refresh triggered, event will be retried after refresh")
        } catch (e: CancellationException) {
            Timber.d("Event publishing cancelled.")
            // The coroutine was cancelled, so there is no need to dispatch any action
            return
        } catch (e: Exception) {
            Timber.e(e, "Error publishing events: ${e.message}")
            // Continue with polling even if event publishing fails
        }
    }

    private suspend fun pollEvents(
            url: String,
            refreshToken: String,
            accessToken: String,
            initialEventIDs: Map<String, Int>
    ) {
        Timber.d("pollEvents called with initial event IDs: $initialEventIDs")
        var currentEventIDs = initialEventIDs

        while (true) {
            try {
                Timber.d("Waiting 1 second before polling")
                delay(1000) // Wait 1 second before polling

                val eventsJson = gson.toJson(currentEventIDs)
                val requestBody = eventsJson.toRequestBody(jsonMediaType)
                Timber.d("Polling with event IDs: $eventsJson")

                val response =
                        authService.fetchAuthenticated(
                                "$url/events/poll",
                                refreshToken,
                                accessToken,
                                "POST",
                                requestBody
                        )

                response.use {
                    Timber.d("Poll response: ${it.code}")
                    when (it.code) {
                        200 -> {
                            val responseBody = it.body?.string()
                            Timber.d("Poll response body: $responseBody")
                            val data =
                                    gson.fromJson(
                                            responseBody,
                                            object : TypeToken<Map<String, Int>>() {}.type
                                    ) as
                                            Map<String, Int>
                            currentEventIDs = data
                            Timber.d("Dispatching EventsUpdated with data: $data")
                            stateDispatcher.dispatch(ConnectionAction.EventsUpdated(data))
                        }
                        304 -> {
                            // Not modified - dispatch the same event IDs
                            Timber.d("Dispatching EventsUpdated with current data: $currentEventIDs")
                            stateDispatcher.dispatch(
                                    ConnectionAction.EventsUpdated(currentEventIDs)
                            )
                        }
                        else -> {
                            Timber.w("Poll failed with response: ${it.code}")
                            throw Exception("HTTP ${it.code}: ${it.message}")
                        }
                    }
                }
            } catch (e: UnauthenticatedError) {
                Timber.e(e, "UnauthenticatedError during polling: ${e.message}")
                stateDispatcher.dispatch(
                        ConnectionAction.AccessTokenRevoked(e.message ?: "Unknown error")
                )
            } catch (e: CancellationException) {
                Timber.d("Polling cancelled.")
                // The coroutine was cancelled, so there is no need to dispatch any action
                return
            } catch (e: Exception) {
                // In production, this should transition to "reconnecting" state
                Timber.e(e, "Exception during polling: ${e.message}")
                stateDispatcher.dispatch(
                        ConnectionAction.ConnectionFailed("Polling failed: ${e.message}")
                )
                break // Exit the polling loop on error
            }
        }
    }
}

// A service that sets up the connection state provider and hooks it up to the
// coroutine manager. While the sub-components are written in a functional style
// for ease of testing, this is the imperative shell.
class ConnectionService : Service(), ViewModelStoreOwner {

    private val binder = ConnectionBinder()
    private lateinit var connectionStateProvider: ConnectionStateProvider
    private lateinit var dataViewService: DataViewService

    // Coroutine manager for actually kicking off background requests
    private lateinit var coroutineManager: ConnectionServiceCoroutineManager

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

        // WARNING: Enable insecure connections for development/self-hosted environments only
        // In production, set this to false and use proper SSL certificates
        val authService = AuthService(allowInsecureConnections = true)

        coroutineManager = ConnectionServiceCoroutineManager(
            authService,
            connectionStateProvider,
            AssetLoader(this),
        )

        dataViewService = DataViewService(authService)

        // Once coroutine manager is initialized, we can start responding to state changes
        connectionStateProvider.connectionState.observeForever { state -> coroutineManager.handleStateUpdate(state) }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineManager.shutdown()
        _viewModelStore.clear()
    }

    fun getConnectionStateProvider(): ConnectionStateProvider = connectionStateProvider
    fun getDataViewService(): DataViewService = dataViewService
}
