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

/**
 * Component Registration Error Tests - Section 4 from tests.md
 * 
 * Tests error scenarios during component registration and installation:
 * - Missing component binaries
 * - Hash validation failures
 * - Multiple component installation failures
 * - Missing component hashes
 * - Missing instance IDs in installation responses
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComponentRegistrationTest {

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

        // Create state provider
        stateDispatcher = ConnectionStateProvider()

        // Initialize with default assetLoader - individual tests can override
        assetLoader = object : AssetLoaderInterface {
            override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
                return ConcurrentHashMap(mapOf("tasks" to "test-hash-123"))
            }

            override fun loadComponentBinary(componentName: String): ByteArray? {
                return "mock-binary-data".toByteArray()
            }
        }

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
    fun testComponentBinaryMissing() {
        // Setup AssetLoader that returns null for component binary
        assetLoader = object : AssetLoaderInterface {
            override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
                return ConcurrentHashMap(mapOf("tasks" to "test-hash-123"))
            }

            override fun loadComponentBinary(name: String): ByteArray? {
                return null // Simulate missing binary
            }
        }
        coroutineManager = ConnectionServiceCoroutineManager(authService, stateDispatcher, assetLoader)

        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Queue responses for login, access token, and registration
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

        // App registration response indicating component needs installation
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": null}")
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
        assertTrue("Error message should mention missing component binary",
            errorState.connectionError!!.contains("binary", ignoreCase = true) ||
            errorState.connectionError!!.contains("component", ignoreCase = true))
    }

    @Test
    fun testComponentHashMismatch() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup AssetLoader with valid binary
        assetLoader = object : AssetLoaderInterface {
            override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
                return ConcurrentHashMap(mapOf("tasks" to "test-hash-123"))
            }

            override fun loadComponentBinary(name: String): ByteArray? {
                return "mock-binary-data".toByteArray()
            }
        }
        coroutineManager = ConnectionServiceCoroutineManager(authService, stateDispatcher, assetLoader)

        // Queue responses for login and access token
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

        // App registration response indicating component needs installation
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": null}")
        )

        // Component installation response with hash mismatch error
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\": \"Hash mismatch\"}")
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
        assertTrue("Error message should indicate installation failure",
            errorState.connectionError!!.contains("Failed to install component", ignoreCase = true) ||
            errorState.connectionError!!.contains("installation", ignoreCase = true))
    }

    @Test
    fun testMultipleComponentsOneFailsInstallation() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup AssetLoader with multiple components
        assetLoader = object : AssetLoaderInterface {
            override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
                return ConcurrentHashMap(mapOf(
                    "tasks" to "test-hash-123",
                    "notes" to "test-hash-456"
                ))
            }

            override fun loadComponentBinary(name: String): ByteArray? {
                return "mock-binary-data-$name".toByteArray()
            }
        }
        coroutineManager = ConnectionServiceCoroutineManager(authService, stateDispatcher, assetLoader)

        // Queue responses for login and access token
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

        // App registration response indicating both components need installation
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": null, \"notes\": null}")
        )

        // First component installation succeeds
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"instanceId\": \"instance-1\"}")
        )

        // Second component installation fails
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
    fun testMissingComponentHash() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup AssetLoader that returns empty hash map
        assetLoader = object : AssetLoaderInterface {
            override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
                return ConcurrentHashMap() // Empty map - no hashes
            }

            override fun loadComponentBinary(name: String): ByteArray? {
                return "mock-binary-data".toByteArray()
            }
        }
        coroutineManager = ConnectionServiceCoroutineManager(authService, stateDispatcher, assetLoader)

        // Queue responses for login and access token
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
        assertTrue("Error message should mention missing component hashes",
            errorState.connectionError!!.contains("hash", ignoreCase = true) ||
            errorState.connectionError!!.contains("missing", ignoreCase = true))
    }

    @Test
    fun testComponentInstanceIdMissingInResponse() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup AssetLoader with valid binary
        assetLoader = object : AssetLoaderInterface {
            override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
                return ConcurrentHashMap(mapOf("tasks" to "test-hash-123"))
            }

            override fun loadComponentBinary(name: String): ByteArray? {
                return "mock-binary-data".toByteArray()
            }
        }
        coroutineManager = ConnectionServiceCoroutineManager(authService, stateDispatcher, assetLoader)

        // Queue responses for login, access token, and registration
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

        // App registration response indicating component needs installation
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"tasks\": null}")
        )

        // Component installation response missing instanceId field
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
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
        assertTrue("Error message should indicate installation failure",
            errorState.connectionError!!.contains("Failed to install component", ignoreCase = true) ||
            errorState.connectionError!!.contains("installation", ignoreCase = true))
    }
}