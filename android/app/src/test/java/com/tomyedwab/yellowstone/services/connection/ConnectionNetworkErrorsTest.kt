package com.tomyedwab.yellowstone.services.connection

import AssetLoaderInterface
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.tomyedwab.yellowstone.provider.connection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectionNetworkErrorsTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var authService: AuthService
    private lateinit var stateDispatcher: ConnectionStateProvider
    private lateinit var assetLoader: AssetLoaderInterface
    private lateinit var coroutineManager: ConnectionServiceCoroutineManager
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create real AuthService pointing to mock server
        authService = AuthService(allowInsecureConnections = true)

        // Create mock AssetLoader
        assetLoader = object : AssetLoaderInterface {
            override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
                return ConcurrentHashMap(mapOf("tasks" to "test-hash-123"))
            }

            override fun loadComponentBinary(componentName: String): ByteArray? {
                return "mock-binary-data".toByteArray()
            }
        }

        // Create state provider
        stateDispatcher = ConnectionStateProvider()

        // Create coroutine manager
        coroutineManager = ConnectionServiceCoroutineManager(
            authService,
            stateDispatcher,
            assetLoader
        )
    }

    @After
    fun teardown() {
        coroutineManager.shutdown()
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun testLoginNetworkFailure() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Simulate connection refused by disconnecting at start
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should mention login failure", 
            errorState.connectionError!!.contains("Login failed", ignoreCase = true))
        assertEquals("Password should be preserved for retry", testPassword, errorState.loginPassword)
    }

    @Test
    fun testLoginHTTP401Unauthorized() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue 401 response for login
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Invalid credentials\"}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should indicate authentication failure", 
            errorState.connectionError!!.contains("authentication", ignoreCase = true) ||
            errorState.connectionError!!.contains("credentials", ignoreCase = true))
        assertEquals("Password should be preserved for correction", testPassword, errorState.loginPassword)
    }

    @Test
    fun testLoginHTTP500InternalServerError() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue 500 response for login
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Internal server error\"}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should indicate server error", 
            errorState.connectionError!!.contains("server", ignoreCase = true))
        assertEquals("Password should be preserved for retry", testPassword, errorState.loginPassword)
    }

    @Test
    fun testLoginMissingCookie() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue 200 response without Set-Cookie header (missing YRT cookie)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should mention missing refresh token/cookie", 
            errorState.connectionError!!.contains("refresh", ignoreCase = true) ||
            errorState.connectionError!!.contains("cookie", ignoreCase = true))
    }

    @Test
    fun testAccessTokenNetworkFailure() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl,
            refreshToken = "valid-refresh-token"
        )

        // Queue successful login response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        // Simulate network failure for access token request
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), testAccount.id)
            )
        )

        // Trigger connection with cached refresh token
        stateDispatcher.dispatch(
            ConnectionAction.ConnectionSelected(testAccount.id)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should mention access token refresh failure", 
            errorState.connectionError!!.contains("access token", ignoreCase = true) ||
            errorState.connectionError!!.contains("refresh", ignoreCase = true))
    }

    @Test
    fun testAccessTokenHTTP401RefreshTokenInvalid() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl,
            refreshToken = "expired-refresh-token"
        )

        // Queue 401 response for access token refresh
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Refresh token expired\"}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)
        var hasReachedWaitingForLogin = false

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null && !hasReachedWaitingForLogin) {
                println("Test: WaitingForLogin with error reached!")
                hasReachedWaitingForLogin = true
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), testAccount.id)
            )
        )

        // Trigger connection with expired refresh token
        stateDispatcher.dispatch(
            ConnectionAction.ConnectionSelected(testAccount.id)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        // Find the WaitingForLogin state with error in the state changes
        val waitingForLoginState = stateChanges.find { state -> 
            state is HubConnectionState.WaitingForLogin && state.connectionError != null 
        }
        assertNotNull("Should have reached WaitingForLogin with error", waitingForLoginState)

        val errorState = waitingForLoginState as HubConnectionState.WaitingForLogin
        assertNull("Account's refreshToken should be null after invalidation", errorState.loginAccount?.refreshToken)
    }

    @Test
    fun testAccessTokenMalformedResponse() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl,
            refreshToken = "valid-refresh-token"
        )

        // Queue malformed access token response (missing access_token field)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"wrong_field\": \"value\"}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), testAccount.id)
            )
        )

        // Trigger connection with cached refresh token
        stateDispatcher.dispatch(
            ConnectionAction.ConnectionSelected(testAccount.id)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should indicate malformed response", 
            errorState.connectionError!!.contains("malformed", ignoreCase = true) ||
            errorState.connectionError!!.contains("invalid", ignoreCase = true) ||
            errorState.connectionError!!.contains("missing access_token", ignoreCase = true))
    }

    @Test
    fun testAppRegistrationNetworkFailure() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue successful login and access token responses
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        // Simulate network failure for app registration request
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should mention app registration failure", 
            errorState.connectionError!!.contains("registration", ignoreCase = true) ||
            errorState.connectionError!!.contains("component", ignoreCase = true))
        assertEquals("Password should be preserved for retry", testPassword, errorState.loginPassword)
    }

    @Test
    fun testAppRegistrationHTTP401AccessTokenRevoked() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue successful login and access token responses
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        // Queue 401 response for app registration (access token revoked)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Access token expired\"}")
        )

        // Queue successful access token refresh
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-3; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"new-access-token\"}")
        )

        // Queue successful app registration with new token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        // Queue event poll responses
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(15, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for Connected state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        // Verify that we went through RefreshingAccessToken state (automatic retry)
        val stateTypes = stateChanges.map { it::class.simpleName }
        assertTrue("Should transition through RefreshingAccessToken state", 
            stateTypes.contains("RefreshingAccessToken"))

        val connectedState = finalState as HubConnectionState.Connected
        assertEquals("Refresh token should be preserved", "test-refresh-token-3", connectedState.refreshToken)
    }

    @Test
    fun testAppRegistrationHTTP500() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue successful login and access token responses
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        // Queue 500 response for app registration
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Internal server error\"}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should indicate server error", 
            errorState.connectionError!!.contains("server", ignoreCase = true))
    }

    @Test
    fun testComponentInstallationNetworkFailure() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue successful login and access token responses
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        // Queue app registration response indicating component needs installation
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": null}")
        )

        // Simulate network failure for component installation request
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should mention component installation failure", 
            errorState.connectionError!!.contains("installation", ignoreCase = true) ||
            errorState.connectionError!!.contains("component", ignoreCase = true))
    }

    @Test
    fun testComponentInstallationHTTP500() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue successful login and access token responses
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        // Queue app registration response indicating component needs installation
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": null}")
        )

        // Queue 500 response for component installation
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Installation failed\"}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: WaitingForLogin with error reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should mention component installation failure", 
            errorState.connectionError!!.contains("installation", ignoreCase = true) ||
            errorState.connectionError!!.contains("component", ignoreCase = true))
    }

    @Test
    fun testInitialEventSyncNetworkFailure() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue responses through app registration
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        // Queue successful first event poll response (to establish connection)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        // Queue successful second event poll response (to complete connection)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0}")
        )

        // Now simulate network failure for subsequent event polling
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)
        var hasReachedConnectedState = false

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            
            // Track when we reach Connected state
            if (state is HubConnectionState.Connected) {
                hasReachedConnectedState = true
                println("Test: Connected state reached, now monitoring for connection loss")
            }
            
            // Only respond to connection failure after we've reached Connected state
            if (hasReachedConnectedState && state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: Connection lost after reaching Connected state!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for error state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should mention event sync failure", 
            errorState.connectionError!!.contains("sync", ignoreCase = true) ||
            errorState.connectionError!!.contains("event", ignoreCase = true) ||
            errorState.connectionError!!.contains("polling", ignoreCase = true))
    }

    @Test
    fun testInitialEventSyncHTTP401() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue responses through app registration
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        // Queue 401 response for event poll (access token revoked)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Access token expired\"}")
        )

        // Queue successful access token refresh
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-3; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"new-access-token\"}")
        )

        // Queue successful event poll with new token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(15, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for Connected state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        // Verify that we went through RefreshingAccessToken state (automatic retry)
        val stateTypes = stateChanges.map { it::class.simpleName }
        assertTrue("Should transition through RefreshingAccessToken state", 
            stateTypes.contains("RefreshingAccessToken"))

        val connectedState = finalState as HubConnectionState.Connected
        assertEquals("Should use new access token", "new-access-token", connectedState.accessToken)
    }

    @Test
    fun testConnectedEventPollingNetworkFailure() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // First, establish connection successfully
        // 1. Login response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        // 2. Access token response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        // 3. App registration response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        // 4. First event poll response (initializes connection)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        // 5. Second event poll response (connection established)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                connectedLatch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and connect
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        connectedLatch.await(10, TimeUnit.SECONDS)

        // Now test network failure during polling
        // Simulate network failure by disconnecting at start
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        val errorLatch = CountDownLatch(1)
        var hasReachedConnectedState = false

        // Update observer to track disconnection
        stateDispatcher.connectionState.removeObserver(observer)
        val errorObserver = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            
            // Track when we reach Connected state
            if (state is HubConnectionState.Connected) {
                hasReachedConnectedState = true
                println("Test: Connected state reached, now monitoring for connection loss")
            }
            
            // Only respond to connection failure after we've reached Connected state
            if (hasReachedConnectedState && state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                println("Test: Connection lost after reaching Connected state!")
                errorLatch.countDown()
            }
        }
        stateDispatcher.connectionState.observeForever(errorObserver)

        ShadowLooper.idleMainLooper()
        val errorCompleted = errorLatch.await(15, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(errorObserver)

        assertTrue("Test timed out waiting for connection loss", errorCompleted)

        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin", finalState is HubConnectionState.WaitingForLogin)

        val errorState = finalState as HubConnectionState.WaitingForLogin
        assertNotNull("connectionError should not be null", errorState.connectionError)
        assertTrue("Error message should mention polling failure", 
            errorState.connectionError!!.contains("polling", ignoreCase = true) ||
            errorState.connectionError!!.contains("connection", ignoreCase = true))
    }

    @Test
    fun testConnectedEventPollingHTTP401() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // First, establish connection successfully
        // 1. Login response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        // 2. Access token response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        // 3. App registration response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        // 4. First event poll response (initializes connection)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        // 5. Second event poll response (connection established)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                connectedLatch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and connect
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        connectedLatch.await(10, TimeUnit.SECONDS)

        // Now test 401 during polling
        // 6. Event poll response with 401 (access token revoked)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Access token expired\"}")
        )

        // 7. Access token refresh response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-3; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"new-access-token\"}")
        )

        // 8. Event poll response with new token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 1}")
        )

        val recoveredLatch = CountDownLatch(1)

        // Update observer to track recovery
        stateDispatcher.connectionState.removeObserver(observer)
        val recoveryObserver = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected && state.accessToken == "new-access-token") {
                println("Test: Connected state with new token reached!")
                recoveredLatch.countDown()
            }
        }
        stateDispatcher.connectionState.observeForever(recoveryObserver)

        ShadowLooper.idleMainLooper()
        val recovered = recoveredLatch.await(15, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(recoveryObserver)

        assertTrue("Test timed out waiting for token refresh recovery", recovered)

        val finalState = stateChanges.last()
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        val connectedState = finalState as HubConnectionState.Connected
        assertEquals("Should use new access token", "new-access-token", connectedState.accessToken)
        assertEquals("Event ID should be updated", 1, connectedState.backendEventIDs["instance-123"])

        // Verify connection was maintained (not lost)
        val errorStates = stateChanges.filter { it is HubConnectionState.WaitingForLogin && it.connectionError != null }
        assertEquals("No error states should occur during token refresh", 0, errorStates.size)
    }

    @Test
    fun testEventPublishingNetworkFailure() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // First, establish connection successfully
        // (Queue the same responses as in previous tests)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        // Add many polling responses for initial sync and regular polling
        // Use a sequence that will ensure event ID gets to 1
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )
        
        // Add multiple responses with event ID 1 to ensure it gets updated
        for (i in 1..15) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"instance-123\": 1}")
            )
        }

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                connectedLatch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and connect
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        connectedLatch.await(10, TimeUnit.SECONDS)

        // Now test event publishing with network failure
        val testEvent = PendingEvent(
            clientId = "test-event-1",
            type = "task_created",
            timestamp = "2024-01-01T00:00:00Z",
            data = mapOf("task" to "Test Task")
        )

        // Don't queue publish event response to simulate network failure
        // But queue event poll response to show polling continues
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 1}")
        )

        val publishStateChanges = mutableListOf<HubConnectionState>()

        // Update observer to track event publishing
        stateDispatcher.connectionState.removeObserver(observer)
        val publishObserver = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            if (state is HubConnectionState.Connected) {
                publishStateChanges.add(state)
                println("Test: Connected state - pending events: ${state.pendingEvents.size}")
            }
        }
        stateDispatcher.connectionState.observeForever(publishObserver)

        // Publish the event
        stateDispatcher.dispatch(
            ConnectionAction.PublishEvent(testEvent)
        )

        ShadowLooper.idleMainLooper()
        Thread.sleep(3000) // Wait for event processing and polling
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(publishObserver)

        // Verify event publishing failed but connection maintained
        val finalState = publishStateChanges.lastOrNull()
        assertNotNull("Should still be in Connected state", finalState)
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        val connectedState = finalState as HubConnectionState.Connected
        // Event should remain in pending queue for retry
        assertEquals("Event should remain in pending queue after publish failure", 1, connectedState.pendingEvents.size)
        assertEquals("Event clientId should match", "test-event-1", connectedState.pendingEvents[0].clientId)

        // Verify polling continues (event ID should be updated)
        assertEquals("Event ID should be updated by polling", 1, connectedState.backendEventIDs["instance-123"])
    }

    @Test
    fun testEventPublishingHTTP401() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // First, establish connection successfully
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                connectedLatch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and connect
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        connectedLatch.await(10, TimeUnit.SECONDS)

        // Now test event publishing with 401 response
        val testEvent = PendingEvent(
            clientId = "test-event-1",
            type = "task_created",
            timestamp = "2024-01-01T00:00:00Z",
            data = mapOf("task" to "Test Task")
        )

        // Queue 401 response for publish event (access token revoked)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Access token expired\"}")
        )

        // Queue access token refresh response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-3; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"new-access-token\"}")
        )

        // Queue app registration response (component already installed)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        // Queue initial event poll response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        // Queue second event poll response (connection established)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0}")
        )

        // Queue successful publish event retry
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
        )

        // Queue event poll response after publish
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 1}")
        )

        val publishStateChanges = mutableListOf<HubConnectionState>()
        val allStateChanges = mutableListOf<HubConnectionState>()

        // Update observer to track event publishing
        stateDispatcher.connectionState.removeObserver(observer)
        val publishObserver = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            allStateChanges.add(state)
            println("Test: Calling handleStateUpdate for state: ${state::class.simpleName}")
            coroutineManager.handleStateUpdate(state) // Always call handleStateUpdate
            println("Test: handleStateUpdate completed")
            if (state is HubConnectionState.Connected) {
                publishStateChanges.add(state)
                println("Test: Connected state - pending events: ${state.pendingEvents.size}")
            } else if (state is HubConnectionState.Connecting.RefreshingAccessToken) {
                println("Test: RefreshingAccessToken state detected")
            }
        }
        stateDispatcher.connectionState.observeForever(publishObserver)

        // Publish the event
        stateDispatcher.dispatch(
            ConnectionAction.PublishEvent(testEvent)
        )

        ShadowLooper.idleMainLooper()
        Thread.sleep(15000) // Wait even longer for full reconnection process
        ShadowLooper.idleMainLooper()
        
        // Print final request count to debug
        println("Test: Total requests made: ${mockWebServer.requestCount}")
        
        // Print the actual requests that were made
        for (i in 0 until mockWebServer.requestCount) {
            try {
                val request = mockWebServer.takeRequest()
                println("Test: Request ${i + 1}: ${request.method} ${request.path}")
            } catch (e: Exception) {
                println("Test: Error getting request ${i + 1}: ${e.message}")
            }
        }

        stateDispatcher.connectionState.removeObserver(publishObserver)

        // Verify event was preserved and eventually published
        println("Test: Final publishStateChanges count: ${publishStateChanges.size}")
        println("Test: All state changes during publish phase:")
        allStateChanges.forEachIndexed { index, state ->
            if (state is HubConnectionState.Connected) {
                println("Test:  [$index] ${state::class.simpleName} - pending events: ${state.pendingEvents.size}")
            } else {
                println("Test:  [$index] ${state::class.simpleName}")
            }
        }
        
        val finalState = publishStateChanges.lastOrNull()
        assertNotNull("Should still be in Connected state", finalState)
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        val connectedState = finalState as HubConnectionState.Connected
        println("Test: Final state pending events: ${connectedState.pendingEvents.size}")
        println("Test: Final state access token: ${connectedState.accessToken}")
        // Event should be removed from pending queue after successful retry
        assertEquals("Event should be removed from pending queue after successful publish", 0, connectedState.pendingEvents.size)

        // Verify token refresh occurred
        assertEquals("Should use new access token", "new-access-token", connectedState.accessToken)
    }

    @Test
    fun testEventPublishingHTTP500() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // First, establish connection successfully
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-2; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                connectedLatch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and connect
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        connectedLatch.await(10, TimeUnit.SECONDS)

        // Now test event publishing with 500 response
        val testEvent = PendingEvent(
            clientId = "test-event-1",
            type = "task_created",
            timestamp = "2024-01-01T00:00:00Z",
            data = mapOf("task" to "Test Task")
        )

        // Queue 500 response for publish event (add it right before the event publishing)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Server error\"}")
        )

        // Queue event poll response to show polling continues
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 1}")
        )

        // Queue 500 response for publish event
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Server error\"}")
        )

        // Queue event poll responses to ensure initial sync completes with event ID 1
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 1}")
        )
        
        // Add more polling responses for regular polling after connection is established
        // Add enough responses to ensure polling doesn't consume the 500 response
        for (i in 1..2) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"instance-123\": 1}")
            )
        }

        val publishStateChanges = mutableListOf<HubConnectionState>()

        // Update observer to track event publishing
        stateDispatcher.connectionState.removeObserver(observer)
        val publishObserver = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            if (state is HubConnectionState.Connected) {
                publishStateChanges.add(state)
                println("Test: Connected state - pending events: ${state.pendingEvents.size}")
            }
        }
        stateDispatcher.connectionState.observeForever(publishObserver)

        // Publish the event
        stateDispatcher.dispatch(
            ConnectionAction.PublishEvent(testEvent)
        )

        ShadowLooper.idleMainLooper()
        Thread.sleep(3000) // Wait for event processing and polling
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(publishObserver)

        // Verify event publishing failed but connection maintained
        val finalState = publishStateChanges.lastOrNull()
        assertNotNull("Should still be in Connected state", finalState)
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        val connectedState = finalState as HubConnectionState.Connected
        // Event should remain in pending queue for retry
        assertEquals("Event should remain in pending queue after publish failure", 1, connectedState.pendingEvents.size)
        assertEquals("Event clientId should match", "test-event-1", connectedState.pendingEvents[0].clientId)

        // Verify polling continues (event ID should be updated from initial -1)
        val finalEventId = connectedState.backendEventIDs["instance-123"]
        assertNotNull("Event ID should be present", finalEventId)
        assertTrue("Event ID should be updated from -1", finalEventId!! > -1)
        println("Test: Final event ID: $finalEventId")
    }
}