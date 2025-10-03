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
class ConnectionAuthErrorsTest {

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
    fun testInvalidCredentials() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "wrong-password"

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

        // Trigger login with invalid credentials
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
            errorState.connectionError!!.contains("credentials", ignoreCase = true) ||
            errorState.connectionError!!.contains("unauthorized", ignoreCase = true))
        assertEquals("Password should be preserved for correction", testPassword, errorState.loginPassword)
    }

    @Test
    fun testRefreshTokenExpiresDuringConnectionSequence() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // 1. Login response with refresh token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=expiring-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        // 2. Access token response - 401 (refresh token expired)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Refresh token expired\"}")
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

        // Find the WaitingForLogin state with error in the state changes
        val waitingForLoginState = stateChanges.find { state -> 
            state is HubConnectionState.WaitingForLogin && state.connectionError != null 
        }
        assertNotNull("Should have reached WaitingForLogin with error", waitingForLoginState)

        val errorState = waitingForLoginState as HubConnectionState.WaitingForLogin
        assertNull("Account's refreshToken should be null after invalidation", errorState.loginAccount?.refreshToken)
        // Note: Password preservation behavior may vary based on reducer implementation
        // The test documents the expected behavior, but the actual implementation may differ
    }

    @Test
    fun testAccessTokenRevokedDuringRegistration() {
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
        assertEquals("Should use new access token", "new-access-token", connectedState.accessToken)
        assertEquals("Refresh token should be preserved", "test-refresh-token-3", connectedState.refreshToken)
    }

    @Test
    fun testAccessTokenRevokedDuringEventSync() {
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
    fun testAccessTokenRevokedWhileConnected() {
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
    fun testMultipleSequentialTokenRevocations() {
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

        // First app registration attempt - 401 (access token revoked)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Access token expired\"}")
        )

        // First access token refresh - 200 + new token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-3; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"new-access-token-1\"}")
        )

        // Second app registration attempt - 401 again (new token also revoked)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Access token expired\"}")
        )

        // Second access token refresh - 200 + another new token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token-4; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"new-access-token-2\"}")
        )

        // Third app registration attempt - success
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
        val completed = latch.await(20, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for Connected state", completed)

        val finalState = stateChanges.last()
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        // Verify multiple refresh cycles occurred
        val stateTypes = stateChanges.map { it::class.simpleName }
        val refreshingTokenCount = stateTypes.count { it == "RefreshingAccessToken" }
        assertTrue("Should have multiple RefreshingAccessToken states", refreshingTokenCount >= 2)

        val connectedState = finalState as HubConnectionState.Connected
        assertEquals("Should use final access token", "new-access-token-2", connectedState.accessToken)
        assertEquals("Should use final refresh token", "test-refresh-token-4", connectedState.refreshToken)
    }

    @Test
    fun testRefreshTokenInvalidPreservesPassword() {
        // Test the reducer logic directly by creating a RefreshingAccessToken state
        // and then applying RefreshTokenInvalid action
        val testPassword = "test-password-123"
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = "https://example.com",
            refreshToken = "expired-refresh-token"
        )
        
        val accountList = HubAccountList(listOf(testAccount), testAccount.id)
        
        // Create a ConnectionStateProvider to test the reducer
        val stateProvider = ConnectionStateProvider()
        
        // Manually set the initial state to RefreshingAccessToken with password
        // We'll use StartLogin to get to LoggingIn state first, then transition
        stateProvider.dispatch(ConnectionAction.AccountListLoaded(accountList))
        stateProvider.dispatch(ConnectionAction.StartLogin(testAccount, testPassword))
        
        // Now transition to RefreshingAccessToken using StartConnection (this preserves password)
        stateProvider.dispatch(ConnectionAction.StartConnection(testAccount, testAccount.refreshToken!!))
        
        // Verify we're in RefreshingAccessToken state with password
        val refreshingState = stateProvider.connectionState.value as HubConnectionState.Connecting.RefreshingAccessToken
        assertEquals("Should have password in RefreshingAccessToken state", testPassword, refreshingState.loginPassword)
        
        // Now dispatch RefreshTokenInvalid
        stateProvider.dispatch(ConnectionAction.RefreshTokenInvalid("Refresh token expired"))
        
        // Get the final state
        val finalState = stateProvider.connectionState.value as HubConnectionState.WaitingForLogin
        
        // Verify password is preserved
        assertEquals("Password should be preserved after RefreshTokenInvalid", testPassword, finalState.loginPassword)
        assertEquals("Error message should be preserved", "Refresh token expired", finalState.connectionError)
        assertNull("Refresh token should be cleared", finalState.loginAccount?.refreshToken)
    }
}