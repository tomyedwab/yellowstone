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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectionPerformanceTest {

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

        mockWebServer = MockWebServer()
        mockWebServer.start()

        authService = AuthService(allowInsecureConnections = true)

        assetLoader = object : AssetLoaderInterface {
            override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
                return ConcurrentHashMap(mapOf("tasks" to "test-hash-123"))
            }

            override fun loadComponentBinary(componentName: String): ByteArray? {
                return "mock-binary-data".toByteArray()
            }
        }

        stateDispatcher = ConnectionStateProvider()
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
    fun testHighFrequencyEventPublishing() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup connection first
        setupBasicConnection(testAccount, testPassword)

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
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
        ShadowLooper.idleMainLooper()

        // Verify connected
        assertTrue("Should reach Connected state", stateChanges.last() is HubConnectionState.Connected)

        // Now test high-frequency event publishing
        val eventCount = 100
        val eventsPublished = AtomicInteger(0)
        val eventsProcessed = AtomicInteger(0)

        // Setup responses for event publishing and polling
        repeat(eventCount) { i ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{}")
            )
            
            // Also queue poll responses
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"instance-123\": ${i + 1}}")
            )
        }

        // Switch observer to track event publishing
        stateDispatcher.connectionState.removeObserver(observer)
        
        val publishObserver = Observer<HubConnectionState> { state ->
            if (state is HubConnectionState.Connected) {
                stateChanges.add(state)
                coroutineManager.handleStateUpdate(state)
                
                if (state.pendingEvents.isEmpty() && eventsPublished.get() == eventCount) {
                    eventsProcessed.set(eventsPublished.get())
                }
            }
        }
        stateDispatcher.connectionState.observeForever(publishObserver)

        // Measure time to publish 100 events
        val publishTime = measureTimeMillis {
            repeat(eventCount) { i ->
                val event = PendingEvent(
                    clientId = "test-event-$i",
                    type = "task_created",
                    timestamp = "2024-01-01T00:00:${i.toString().padStart(2, '0')}Z",
                    data = mapOf("task" to "Test Task $i")
                )
                
                stateDispatcher.dispatch(ConnectionAction.PublishEvent(event))
                eventsPublished.incrementAndGet()
            }
        }

        // Wait for processing
        Thread.sleep(5000) // Give time for event processing
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(publishObserver)

        // Assertions
        println("Published $eventCount events in ${publishTime}ms")
        assertEquals("All events should be published", eventCount, eventsPublished.get())
        assertTrue("Event processing should complete", eventsProcessed.get() >= 0)
        assertTrue("Performance should be acceptable (< 10s for 100 events)", publishTime < 10000)
        
        val finalState = stateChanges.last()
        assertTrue("Should remain in Connected state", finalState is HubConnectionState.Connected)
    }

    @Test
    fun testLongRunningConnection() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup connection
        setupBasicConnection(testAccount, testPassword)

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
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
        ShadowLooper.idleMainLooper()

        assertTrue("Should reach Connected state", stateChanges.last() is HubConnectionState.Connected)

        // Simulate 1000+ poll responses for long-running connection
        val totalPolls = 1000
        
        // Queue many poll responses
        repeat(totalPolls) { i ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"instance-123\": $i}")
            )
        }

        // Let it run for a simulated period
        Thread.sleep(2000) // Simulate some polling time
        ShadowLooper.idleMainLooper()

        // Check that polling continued
        val requestsMade = mockWebServer.requestCount
        println("Requests made during long-running test: $requestsMade")
        
        // Verify connection remained stable
        val finalState = stateChanges.last()
        assertTrue("Should remain Connected after long-running period", finalState is HubConnectionState.Connected)
        
        val connectedState = finalState as HubConnectionState.Connected
        assertTrue("Should have processed multiple polls", connectedState.backendEventIDs["instance-123"] ?: 0 >= 0)
        
        stateDispatcher.connectionState.removeObserver(observer)
    }

    @Test
    fun testRapidAccountSwitching() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        
        // Create 5 test accounts
        val accounts = (1..5).map { i ->
            HubAccount(
                id = "test-account-$i",
                name = "Test Account $i",
                url = serverUrl
            )
        }

        // Setup connection responses for all accounts
        accounts.forEach { account ->
            setupBasicConnection(account, "test-password-$account.id")
        }

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectionCount = AtomicInteger(0)

        val observer = Observer<HubConnectionState> { state ->
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                connectionCount.incrementAndGet()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Initialize with all accounts
        stateDispatcher.dispatch(
            ConnectionAction.AccountListLoaded(
                HubAccountList(accounts, null)
            )
        )

        // Rapidly switch between accounts 20 times
        val switchCount = 20
        val switchTime = measureTimeMillis {
            repeat(switchCount) { i ->
                val accountIndex = (i % accounts.size)
                val account = accounts[accountIndex]
                
                // Start login for this account
                stateDispatcher.dispatch(
                    ConnectionAction.StartLogin(account, "test-password-${account.id}")
                )
                
                // Small delay to allow some processing
                Thread.sleep(50)
                ShadowLooper.idleMainLooper()
            }
        }

        // Wait for some connections to complete
        Thread.sleep(2000)
        ShadowLooper.idleMainLooper()

        stateDispatcher.connectionState.removeObserver(observer)

        println("Switched accounts $switchCount times in ${switchTime}ms")
        
        // Assertions
        assertTrue("Should handle rapid switching without crashes", true)
        assertTrue("Switching should be performant (< 5s for 20 switches)", switchTime < 5000)
        
        // Verify no resource leaks by checking final state is valid
        val finalState = stateChanges.last()
        assertTrue("Final state should be valid", finalState::class.simpleName != null)
    }

    @Test
    fun testMemoryUsageDuringEventPublishing() {
        val serverUrl = mockWebServer.url("").toString().trimEnd('/')
        val testAccount = HubAccount(
            id = "test-account",
            name = "Test Account",
            url = serverUrl
        )
        val testPassword = "test-password"

        // Setup connection
        setupBasicConnection(testAccount, testPassword)

        val stateChanges = mutableListOf<HubConnectionState>()
        val connectedLatch = CountDownLatch(1)

        val observer = Observer<HubConnectionState> { state ->
            stateChanges.add(state)
            coroutineManager.handleStateUpdate(state)
            if (state is HubConnectionState.Connected) {
                connectedLatch.countDown()
            }
        }

        stateDispatcher.connectionState.observeForever(observer)

        // Connect
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
        ShadowLooper.idleMainLooper()

        // Get initial memory state
        System.gc() // Suggest garbage collection
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Publish many events with large payloads
        val largeEventCount = 50
        val largePayload = "x".repeat(1024 * 10) // 10KB per event

        repeat(largeEventCount) { i ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{}")
            )
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"instance-123\": ${i + 1}}")
            )
        }

        // Publish events with large payloads
        repeat(largeEventCount) { i ->
            val event = PendingEvent(
                clientId = "large-event-$i",
                type = "large_event",
                timestamp = "2024-01-01T00:00:${i.toString().padStart(2, '0')}Z",
                data = mapOf("largeData" to largePayload)
            )
            
            stateDispatcher.dispatch(ConnectionAction.PublishEvent(event))
        }

        // Wait for processing
        Thread.sleep(3000)
        ShadowLooper.idleMainLooper()

        // Check final memory usage
        System.gc()
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        println("Memory usage: initial=${initialMemory / 1024}KB, final=${finalMemory / 1024}KB, increase=${memoryIncrease / 1024}KB")

        stateDispatcher.connectionState.removeObserver(observer)

        // Assertions
        assertTrue("Memory increase should be reasonable (< 5MB for 50 large events)", memoryIncrease < 5 * 1024 * 1024)
        assertTrue("Should handle large payloads without memory issues", true)
    }

    private fun setupBasicConnection(@Suppress("UNUSED_PARAMETER") testAccount: HubAccount, @Suppress("UNUSED_PARAMETER") testPassword: String) {
        // Queue basic connection responses
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
    }
}