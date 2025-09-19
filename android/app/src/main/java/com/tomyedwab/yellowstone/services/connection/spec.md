Connection Service
==================

This service runs in the background of the Android application and manages the
connection to the server. The actions that it takes are based on the current
Connection State Provider's state and it updates that state by dispatching
actions. The application is responsible for starting and stopping this service,
and can retrieve data using this service as well as publish events to the
backend.

## Architecture

The connection service consists of several components:

### ConnectionService
Main Android Service that:
- Manages the connection lifecycle based on connection state changes
- Handles authentication flow (login → access token → app registration → event sync)
- Polls for backend changes when connected
- Provides a bound service interface for activities to interact with the connection state
- Accepts configurable component asset mapping for flexible binary loading

### AuthService
HTTP authentication utilities that handle:
- **doLogin()**: Authenticates with username/password, returns refresh token
- **refreshAccessToken()**: Gets access token using refresh token
- **fetchAuthenticated()**: Makes authenticated HTTP requests with automatic token refresh

### DataViewService
Data fetching service similar to React's useDataView hook:
- Provides reactive data fetching based on connection state and event IDs
- Includes caching with reference counting and automatic expiry
- Handles loading states and error reporting
- Returns LiveData for UI components to observe

## Service Lifecycle

1. **Startup**: LoginActivity starts and binds to ConnectionService
2. **Component Initialization**: Service receives component asset map via `initializeComponentAssets()`
3. **Asset Loading**: Service loads component hashes from configured assets  
4. **Initialization**: Service loads account list and dispatches AccountListLoaded action
5. **Authentication Flow** (when user logs in):
   - LoginActivity dispatches StartLogin action
   - Service handles login → access token → component registration → event sync
   - On success, dispatches ConnectionSucceeded
6. **Connected State**: Service polls for backend changes and dispatches EventsUpdated
7. **Data Fetching**: UI components use DataViewService to fetch component data reactively

## Component Asset Configuration

The ConnectionService requires initialization with a component asset map that defines which embedded binaries are available:

```kotlin
data class ComponentAsset(
    val binaryAssetName: String,  // Asset file containing the binary
    val md5AssetName: String      // Asset file containing MD5 hash
)

// Example configuration
val componentAssetMap = mapOf(
    "yellowstone" to ComponentAsset(
        binaryAssetName = "tasks.zip",
        md5AssetName = "tasks.md5"
    )
)
```

**Key Methods:**
- `initializeComponentAssets(Map<String, ComponentAsset>)`: Must be called after service binding to configure available components
- `loadComponentBinary(String)`: Lazily loads component binaries when needed during installation

## State Management

The service observes ConnectionStateProvider changes and takes appropriate actions:

- **CONNECTING state**: Handles the multi-step authentication and registration process
- **CONNECTED state**: Starts polling for backend changes
- **Error handling**: Dispatches ConnectionFailed actions on errors

## Usage Example

```kotlin
// In Activity/Fragment
private var connectionService: ConnectionService? = null

private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        connectionService = (service as ConnectionService.ConnectionBinder).getService()
        
        // Initialize component assets (required before service operations)
        connectionService?.initializeComponentAssets(ComponentAssets.COMPONENT_ASSET_MAP)
        
        // Observe connection state
        connectionService?.getConnectionStateProvider()?.connectionState?.observe(this@Activity) { state ->
            // Handle state changes
        }
        
        // Dispatch actions
        connectionService?.getConnectionStateProvider()?.dispatch(
            ConnectionAction.StartLogin(account, password)
        )
    }
    // ...
}

// Start and bind service
val intent = Intent(this, ConnectionService::class.java)
startService(intent)
bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
```

## Data Fetching Example

```kotlin
// Create DataViewService (typically in ViewModel or Repository)
val dataViewService = DataViewService(authService)

// Fetch data reactively
val dataView = dataViewService.createDataView(
    connectionState = connectionStateProvider.connectionState,
    componentName = "my-component",
    apiPath = "api/data",
    apiParams = mapOf("param1" to "value1"),
    typeToken = object : TypeToken<MyDataType>() {}
)

// Observe in UI
dataView.observe(this) { result ->
    when {
        result.loading -> showLoading()
        result.error != null -> showError(result.error)
        result.data != null -> showData(result.data)
    }
}
```
