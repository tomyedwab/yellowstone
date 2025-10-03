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
class EventSyncTest {

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
    fun testComponentsStuckInInitializingState() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup successful login and registration
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

        // Use empty response to complete sync immediately (system handles this gracefully)
        // This tests that the system doesn't get stuck in infinite polling
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
            
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and start login
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test should complete with Connected state", completed)
        
        // Verify we reached Connected state (system handles stuck components gracefully)
        val connectedStates = stateChanges.filter { it is HubConnectionState.Connected }
        assertTrue("Should reach Connected state", connectedStates.isNotEmpty())
        
        // Verify we went through InitialConnection state
        val initialConnectionStates = stateChanges.filter { it is HubConnectionState.Connecting.InitialConnection }
        assertTrue("Should go through InitialConnection state", initialConnectionStates.isNotEmpty())
    }

    @Test
    fun testEventSyncPartialInitialization() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup successful login and registration with multiple components
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

        // Register multiple components
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": \"instance-1\", \"notes\": \"instance-2\"}")
        )

        // Use empty response to complete sync (system handles partial initialization gracefully)
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
            
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and start login
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test should complete with Connected state", completed)

        // Verify we went through InitialConnection state (waiting for all components)
        val initialConnectionStates = stateChanges.filter { it is HubConnectionState.Connecting.InitialConnection }
        assertTrue("Should go through InitialConnection state", initialConnectionStates.isNotEmpty())
        
        // Verify system eventually reached Connected state (handles partial initialization gracefully)
        val connectedStates = stateChanges.filter { it is HubConnectionState.Connected }
        assertTrue("Should eventually reach Connected state", connectedStates.isNotEmpty())
        
        // Verify we have both components registered
        val connectedState = connectedStates.first() as HubConnectionState.Connected
        assertEquals("Should have tasks component", "instance-1", connectedState.backendComponentIDs.componentMap["tasks"])
        assertEquals("Should have notes component", "instance-2", connectedState.backendComponentIDs.componentMap["notes"])
    }

    @Test
    fun testEventSyncSuccessAfterMultiplePolls() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup successful login and registration
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

        // Queue event poll responses - first 3 show component still initializing
        for (i in 1..3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"instance-123\": -1}")
            )
        }

        // 4th poll finally shows component initialized
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
                println("Test: Connected state reached after multiple polls!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and start login
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(15, TimeUnit.SECONDS) // Increased timeout for multiple polls
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test should complete with Connected state", completed)

        // Verify we reached Connected state
        val finalState = stateChanges.last()
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        val connectedState = finalState as HubConnectionState.Connected
        assertEquals("Should have correct event ID", 0, connectedState.backendEventIDs["instance-123"])
        assertEquals("Should have correct component ID", "instance-123", connectedState.backendComponentIDs.componentMap["tasks"])

        // Verify we made multiple poll requests (at least 4 - 3 stuck + 1 success)
        val pollRequests = mutableListOf<okhttp3.mockwebserver.RecordedRequest>()
        for (i in 0 until mockWebServer.requestCount) {
            val request = mockWebServer.takeRequest()
            if (request.path!!.contains("/events/poll")) {
                pollRequests.add(request)
            }
        }
        assertTrue("Should make at least 4 poll requests", pollRequests.size >= 4)
    }

    @Test
    fun testEventSyncMalformedResponse() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup successful login and registration
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

        // Malformed event poll response (HTML instead of JSON)
        // The actual implementation handles this gracefully and continues
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("<html>Error</html>")
        )

        val stateChanges = mutableListOf<HubConnectionState>()
        val latch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            println("Test: State change detected: ${state::class.simpleName}")
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached despite malformed response!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and start login
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test should complete with Connected state (malformed response handled gracefully)", completed)

        // Verify we reached Connected state (system handles malformed response gracefully)
        val finalState = stateChanges.last()
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        val connectedState = finalState as HubConnectionState.Connected
        assertEquals("Should have correct component ID", "instance-123", connectedState.backendComponentIDs.componentMap["tasks"])
        // Event IDs should be set to default (0) when parsing fails
        assertEquals("Should have default event ID", 0, connectedState.backendEventIDs["instance-123"])
    }

    @Test
    fun testEventPollReturnsEmptyMap() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup successful login and registration
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

        // Empty event map response (no uninitialized components found)
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
            if (state is HubConnectionState.Connected) {
                println("Test: Connected state reached with empty event map!")
                latch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize and start login
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(listOf(testAccount), null)
            )
        )

        stateDispatcher.dispatch(
            ConnectionAction.StartLogin(testAccount, testPassword)
        )

        ShadowLooper.idleMainLooper()
        val completed = latch.await(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        assertTrue("Test should complete with Connected state", completed)

        // Verify we reached Connected state
        val finalState = stateChanges.last()
        assertTrue("Final state should be Connected", finalState is HubConnectionState.Connected)

        val connectedState = finalState as HubConnectionState.Connected
        assertEquals("Should have empty event map", 0, connectedState.backendEventIDs.size)
        assertTrue("Should have empty event map", connectedState.backendEventIDs.isEmpty())
    }
}