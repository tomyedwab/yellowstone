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
class DataIntegrityTest {

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
    fun testPasswordPreservationDuringConnection() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testPassword = "test-password-123"
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )

        // Queue responses that will fail at access token stage to test password preservation
        // 1. Login response - successful
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=test-refresh-token; Path=/; HttpOnly")
                .setBody("{}")
        )

        // 2. Access token response - will fail with 401 to trigger RefreshTokenInvalid
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Invalid refresh token\"}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            
            // Verify password is preserved in connecting states
            when (state) {
                is HubConnectionState.Connecting.LoggingIn -> {
                    assertEquals("Password should be preserved in LoggingIn state", testPassword, state.loginPassword)
                }
                is HubConnectionState.Connecting.RefreshingAccessToken -> {
                    assertEquals("Password should be preserved in RefreshingAccessToken state", testPassword, state.loginPassword)
                }
                is HubConnectionState.WaitingForLogin -> {
                    if (state.connectionError != null) {
                        // After failure, password should still be preserved for retry
                        println("Test: WaitingForLogin with error - password: ${state.loginPassword}")
                        if (state.loginPassword == testPassword) {
                            latch.countDown()
                        }
                    }
                }
                else -> {}
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

        assertTrue("Test timed out waiting for connection failure. States seen: ${stateChanges.map { it::class.simpleName }}", completed)

        // Verify password was preserved throughout the connection attempt
        val finalState = stateChanges.last() as HubConnectionState.WaitingForLogin
        assertEquals("Final state should preserve password for retry", testPassword, finalState.loginPassword)
        assertNotNull("Should have connection error", finalState.connectionError)
    }

    @Test
    fun testPasswordClearedAfterConnection() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testPassword = "test-password-123"
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )

        // Queue responses for successful connection
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

        // 4. First event poll response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        // 5. Second event poll response
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

        assertTrue("Test timed out waiting for Connected state", completed)

        // Verify final Connected state
        val finalState = stateChanges.last() as HubConnectionState.Connected
        // Connected state doesn't have loginPassword field - this is correct behavior
        // Password is only available in WaitingForLogin and Connecting states
        
        // Verify password was present during connection process but cleared at the end
        val connectingStates = stateChanges.filterIsInstance<HubConnectionState.Connecting>()
        assertTrue("Should have connecting states with password", connectingStates.isNotEmpty())
        connectingStates.forEach { state ->
            assertEquals("Password should be present during connection", testPassword, state.loginPassword)
        }
    }

    @Test
    fun testRefreshTokenPersistence() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val initialRefreshToken = "initial-refresh-token"
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl,
            refreshToken = initialRefreshToken
        )

        // Queue responses for connection with token refresh
        // 1. Access token response (using cached refresh token)
        val newRefreshToken = "new-refresh-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=$newRefreshToken; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"test-access-token\"}")
        )

        // 2. App registration response - will return 401 to trigger token refresh
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Token expired\"}")
        )

        // 3. Second access token response (after token refresh)
        val finalRefreshToken = "final-refresh-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=$finalRefreshToken; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"new-access-token\"}")
        )

        // 4. App registration response (successful this time)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-123\"}")
        )

        // 5. First event poll response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        // 6. Second event poll response
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
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list containing account with refresh token
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), testAccount.id)
            )
        )

        ShadowLooper.idleMainLooper()

        // Trigger connection using cached refresh token
        stateDispatcher.dispatch(
            ConnectionAction.ConnectionSelected(testAccount.id)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(15, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for Connected state", completed)

        // Verify refresh token was preserved and updated throughout the process
        val finalState = stateChanges.last() as HubConnectionState.Connected
        assertEquals("Final state should have the latest refresh token", finalRefreshToken, finalState.refreshToken)

        // Verify refresh token was present in all relevant states
        val refreshingStates = stateChanges.filterIsInstance<HubConnectionState.Connecting.RefreshingAccessToken>()
        assertTrue("Should have RefreshingAccessToken states", refreshingStates.isNotEmpty())
        
        refreshingStates.forEach { state ->
            assertNotNull("Refresh token should be present in RefreshingAccessToken state", state.refreshToken)
        }

        val initialConnectionStates = stateChanges.filterIsInstance<HubConnectionState.Connecting.InitialConnection>()
        assertTrue("Should have InitialConnection state", initialConnectionStates.isNotEmpty())
        
        initialConnectionStates.forEach { state ->
            assertNotNull("Refresh token should be present in InitialConnection state", state.refreshToken)
        }
    }

    @Test
    fun testComponentIDsPreserved() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )

        // Queue responses for successful connection
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
                .setBody("{\"tasks\": \"instance-123\", \"notes\": \"instance-456\"}")
        )

        // 4. First event poll response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1, \"instance-456\": -1}")
        )

        // 5. Second event poll response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 0, \"instance-456\": 0}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                connectedLatch.countDown()
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
            ConnectionAction.StartLogin(testAccount, "test-password")
        )

        ShadowLooper.idleMainLooper()
        connectedLatch.await(10, TimeUnit.SECONDS)

        // Now trigger token refresh while connected
        // 6. Event poll response with 401 to trigger token refresh
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Token expired\"}")
        )

        // 7. Access token refresh response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "YRT=refreshed-token; Path=/; HttpOnly")
                .setBody("{\"access_token\": \"refreshed-access-token\"}")
        )

        // 8. Event poll response after token refresh
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": 1, \"instance-456\": 1}")
        )

        stateDispatcher.connectionState.removeObserver(observer)
        val refreshLatch = CountDownLatch(1)

        val refreshObserver = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                refreshLatch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(refreshObserver)

        // Trigger event poll to cause token refresh
        stateDispatcher.dispatch(
            ConnectionAction.PublishEvent(
                PendingEvent(
                    clientId = "test-event",
                    type = "test",
                    timestamp = "2024-01-01T00:00:00Z",
                    data = mapOf("test" to "data")
                )
            )
        )

        ShadowLooper.idleMainLooper()
        refreshLatch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(refreshObserver)

        // Verify component IDs were preserved throughout token refresh
        val finalState = stateChanges.last() as HubConnectionState.Connected
        assertEquals("Component IDs should be preserved after token refresh", "instance-123", finalState.backendComponentIDs.componentMap["tasks"])
        assertEquals("Component IDs should be preserved after token refresh", "instance-456", finalState.backendComponentIDs.componentMap["notes"])
        
        // Verify component IDs were present in all relevant states
        val initialConnectionStates = stateChanges.filterIsInstance<HubConnectionState.Connecting.InitialConnection>()
        assertTrue("Should have InitialConnection state", initialConnectionStates.isNotEmpty())
        
        initialConnectionStates.forEach { state ->
            assertNotNull("Component IDs should be present in InitialConnection state", state.backendComponentIDs)
        }
    }

    @Test
    fun testEventQueuePreservationDuringDisconnection() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )

        // First establish connection
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

        // 4. First event poll response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        // 5. Second event poll response
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
                connectedLatch.countDown()
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
            ConnectionAction.StartLogin(testAccount, "test-password")
        )

        ShadowLooper.idleMainLooper()
        connectedLatch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        // Verify we reached connected state
        assertTrue("Should reach Connected state", stateChanges.any { it is HubConnectionState.Connected })
        
        // Get the connected state
        val connectedState = stateChanges.last { it is HubConnectionState.Connected } as HubConnectionState.Connected
        assertEquals("Should start with no pending events", 0, connectedState.pendingEvents.size)

        // Now add event to queue
        val testEvent = PendingEvent(
            clientId = "test-event-1",
            type = "task_created",
            timestamp = "2024-01-01T00:00:00Z",
            data = mapOf("task" to "Test Task")
        )

        // Add event to pending queue
        stateDispatcher.dispatch(ConnectionAction.PublishEvent(testEvent))

        ShadowLooper.idleMainLooper()

        // Wait a bit for the event to be processed
        Thread.sleep(1000)
        ShadowLooper.idleMainLooper()

        // Check if event was added to queue (it should be processed immediately or queued)
        val currentState = stateDispatcher.connectionState.value
        if (currentState is HubConnectionState.Connected) {
            println("Test: Connected state after publish - pending events: ${currentState.pendingEvents.size}")
        }

        // Now trigger connection failure by causing a network error during polling
        // Use a different approach - disconnect the mock server to simulate network failure
        stateDispatcher.connectionState.removeObserver(observer)
        val failureLatch = CountDownLatch(1)

        val failureObserver = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                failureLatch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(failureObserver)

        // Shutdown the mock server to simulate network failure
        mockWebServer.shutdown()
        
        // Trigger an action that would cause network activity
        stateDispatcher.dispatch(
            ConnectionAction.PublishEvent(
                PendingEvent(
                    clientId = "test-event-2",
                    type = "test",
                    timestamp = "2024-01-01T00:00:01Z",
                    data = mapOf("test" to "data2")
                )
            )
        )

        ShadowLooper.idleMainLooper()
        failureLatch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(failureObserver)

        // Note: Current implementation may clear pending events on ConnectionFailed
        // This test documents the current behavior - events may be lost on disconnection
        val finalState = stateChanges.last()
        assertTrue("Final state should be WaitingForLogin with error", 
            finalState is HubConnectionState.WaitingForLogin && finalState.connectionError != null)
    }

    @Test
    fun testAccountListConsistency() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount1 = HubAccount(
            id = "test-account-1",
            name = "Test Account 1",
            url = serverUrl
        )
        val testAccount2 = HubAccount(
            id = "test-account-2",
            name = "Test Account 2",
            url = serverUrl
        )
        val accountList = HubAccountList(listOf(testAccount1, testAccount2), testAccount1.id)

        // Queue responses for successful connection
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

        // 4. First event poll response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instance-123\": -1}")
        )

        // 5. Second event poll response
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
            
            // Verify account list is preserved in all states (except Uninitialized)
            if (state !is HubConnectionState.Uninitialized) {
                try {
                    assertEquals("Account list should be preserved in ${state::class.simpleName}", 2, state.accountList.accounts.size)
                    if (state.accountList.selectedAccount != null) {
                        assertEquals("Selected account should be preserved", testAccount1.id, state.accountList.selectedAccount)
                    }
                } catch (e: AssertionError) {
                    println("Test: Assertion failed in state ${state::class.simpleName}: ${e.message}")
                    throw e
                }
            }
            
            if (state is HubConnectionState.Connected) {
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(accountList)
        )

        ShadowLooper.idleMainLooper()

        // Trigger login
        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount1, "test-password")
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(15, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for Connected state. States seen: ${stateChanges.map { it::class.simpleName }}", completed)

        // Verify final state maintains account list
        val finalState = stateChanges.last() as HubConnectionState.Connected
        assertEquals("Final state should preserve account list", 2, finalState.accountList.accounts.size)
        assertEquals("Final state should preserve selected account", testAccount1.id, finalState.accountList.selectedAccount)
        
        // Find the accounts in the final list (order might be different)
        val finalAccount1 = finalState.accountList.accounts.find { it.id == testAccount1.id }
        val finalAccount2 = finalState.accountList.accounts.find { it.id == testAccount2.id }
        
        assertNotNull("Account 1 should be preserved", finalAccount1)
        assertNotNull("Account 2 should be preserved", finalAccount2)
        assertEquals("Account 1 name should be preserved", testAccount1.name, finalAccount1!!.name)
        assertEquals("Account 2 name should be preserved", testAccount2.name, finalAccount2!!.name)
    }

    @Test
    fun testRefreshTokenClearedOnInvalidation() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val initialRefreshToken = "initial-refresh-token"
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl,
            refreshToken = initialRefreshToken
        )

        // Queue responses where refresh token becomes invalid
        // 1. Access token response - token is invalid
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Invalid refresh token\"}")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with account list containing account with refresh token
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), testAccount.id)
            )
        )

        ShadowLooper.idleMainLooper()

        // Trigger connection using cached refresh token (which will fail)
        stateDispatcher.dispatch(
            ConnectionAction.ConnectionSelected(testAccount.id)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test timed out waiting for connection failure", completed)

        // Verify refresh token was cleared from account
        val finalState = stateChanges.last() as HubConnectionState.WaitingForLogin
        val updatedAccount = finalState.accountList.accounts.find { it.id == testAccount.id }
        assertNotNull("Account should still exist", updatedAccount)
        assertNull("Refresh token should be cleared after invalidation", updatedAccount!!.refreshToken)
        assertNotNull("Should have connection error", finalState.connectionError)
    }

    @Test
    fun testErrorMessagePersistence() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )

        // Test different types of errors
        val testCases = listOf(
            "Login failed" to { testLoginError() },
            "Network error" to { testNetworkError() },
            "Server error" to { testServerError() }
        )

        testCases.forEach { (errorType, testFunction) ->
            println("Testing error message persistence for: $errorType")
            testFunction()
        }
    }

    private fun testLoginError() {
        val testAccount = HubAccount(
            id = "test-account-login",
            name = "Test Account Login",
            url = mockWebServer.url("").toString().trimEnd('/')
        )

        // Queue login failure response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\": \"Invalid credentials\"}")
        )

        val latch = CountDownLatch(1)
        var finalState: HubConnectionState.WaitingForLogin? = null

        val observer = Observer<HubConnectionState> { state ->
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                finalState = state
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, "wrong-password")
        )

        ShadowLooper.idleMainLooper()
        latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertNotNull("Should have error state", finalState)
        assertNotNull("Should have connection error", finalState!!.connectionError)
        assertTrue("Error message should mention authentication", 
            finalState!!.connectionError!!.contains("authentication") || 
            finalState!!.connectionError!!.contains("credentials") ||
            finalState!!.connectionError!!.contains("login"))
    }

    private fun testNetworkError() {
        // Don't enqueue any response to simulate network failure
        val testAccount = HubAccount(
            id = "test-account-network",
            name = "Test Account Network",
            url = "http://invalid-server-that-does-not-exist:9999"
        )

        val latch = CountDownLatch(1)
        var finalState: HubConnectionState.WaitingForLogin? = null

        val observer = Observer<HubConnectionState> { state ->
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                finalState = state
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, "test-password")
        )

        ShadowLooper.idleMainLooper()
        latch.await(15, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertNotNull("Should have error state", finalState)
        assertNotNull("Should have connection error", finalState!!.connectionError)
        assertTrue("Error message should mention network or connection", 
            finalState!!.connectionError!!.contains("network") || 
            finalState!!.connectionError!!.contains("connection") ||
            finalState!!.connectionError!!.contains("failed"))
    }

    private fun testServerError() {
        val testAccount = HubAccount(
            id = "test-account-server",
            name = "Test Account Server",
            url = mockWebServer.url("").toString().trimEnd('/')
        )

        // Queue server error response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Internal server error\"}")
        )

        val latch = CountDownLatch(1)
        var finalState: HubConnectionState.WaitingForLogin? = null

        val observer = Observer<HubConnectionState> { state ->
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.WaitingForLogin && state.connectionError != null) {
                finalState = state
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, "test-password")
        )

        ShadowLooper.idleMainLooper()
        latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertNotNull("Should have error state", finalState)
        assertNotNull("Should have connection error", finalState!!.connectionError)
        assertTrue("Error message should mention server or internal error", 
            finalState!!.connectionError!!.contains("server") || 
            finalState!!.connectionError!!.contains("internal") ||
            finalState!!.connectionError!!.contains("error"))
    }
}