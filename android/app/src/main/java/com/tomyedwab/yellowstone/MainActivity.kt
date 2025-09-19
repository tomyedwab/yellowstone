package com.tomyedwab.yellowstone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.tomyedwab.yellowstone.databinding.ActivityMainBinding
import com.tomyedwab.yellowstone.provider.connection.ConnectionAction
import com.tomyedwab.yellowstone.provider.connection.ConnectionState
import com.tomyedwab.yellowstone.provider.connection.HubAccountList
import com.tomyedwab.yellowstone.provider.storage.ConnectionStorageProvider
import com.tomyedwab.yellowstone.services.connection.ComponentAsset
import com.tomyedwab.yellowstone.services.connection.ConnectionService

object ComponentAssets {
    /**
     * Map of component names to their corresponding asset files
     * This defines which embedded binaries are available for each component
     */
    val COMPONENT_ASSET_MAP = mapOf(
        "yellowstone" to ComponentAsset(
            binaryAssetName = "tasks.zip",
            md5AssetName = "tasks.md5"
        )
        // Add more components here as needed
        // "other-component" to ComponentAsset("other-binary", "other-binary.md5")
    )
}

interface ConnectionServiceListener {
    fun onConnectionServiceReady(connectionService: ConnectionService)
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var connectionService: ConnectionService? = null
    private var isBound = false
    private var isLoginActivityLaunched = false
    private val connectionServiceListeners = mutableListOf<ConnectionServiceListener>()
    private lateinit var storageProvider: ConnectionStorageProvider

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ConnectionService.ConnectionBinder
            connectionService = binder.getService()
            isBound = true
            
            // Initialize the component asset map
            connectionService?.initializeComponentAssets(ComponentAssets.COMPONENT_ASSET_MAP)
            
            setupObservers()
            
            // Notify all listeners that ConnectionService is ready
            connectionService?.let { cs ->
                connectionServiceListeners.forEach { listener ->
                    listener.onConnectionServiceReady(cs)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize storage provider
        storageProvider = ConnectionStorageProvider(this)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_lists, R.id.navigation_labels, R.id.navigation_templates, R.id.navigation_archived
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        
        // Start and bind to the connection service
        val intent = Intent(this, ConnectionService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onResume() {
        super.onResume()
        // Reset login activity flag when MainActivity resumes
        isLoginActivityLaunched = false
        // Check connection state when activity resumes
        connectionService?.getConnectionStateProvider()?.connectionState?.value?.let { state ->
            handleConnectionStateChange(state.state)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupObservers() {
        connectionService?.getConnectionStateProvider()?.connectionState?.observe(this) { state ->
            handleConnectionStateChange(state.state)
            // Refresh the menu when connection state changes
            invalidateOptionsMenu()
        }
    }
    
    private fun handleConnectionStateChange(connectionState: ConnectionState) {
        when (connectionState) {
            ConnectionState.CONNECTED -> {
                // Show main UI
                binding.navView.visibility = android.view.View.VISIBLE
                isLoginActivityLaunched = false
            }
            else -> {
                // Hide main UI and redirect to login (only if not already launched)
                binding.navView.visibility = android.view.View.GONE
                if (!isLoginActivityLaunched) {
                    isLoginActivityLaunched = true
                    startActivity(Intent(this, LoginActivity::class.java))
                }
            }
        }
    }

    fun getConnectionService(): ConnectionService? {
        return connectionService
    }

    fun addConnectionServiceListener(listener: ConnectionServiceListener) {
        connectionServiceListeners.add(listener)
        // If ConnectionService is already available, notify immediately
        connectionService?.let { cs ->
            listener.onConnectionServiceReady(cs)
        }
    }

    fun removeConnectionServiceListener(listener: ConnectionServiceListener) {
        connectionServiceListeners.remove(listener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.account_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val accountInfoItem = menu.findItem(R.id.action_account_info)
        val connectionState = connectionService?.getConnectionStateProvider()?.connectionState?.value

        if (connectionState?.state == ConnectionState.CONNECTED && connectionState.loginAccount != null) {
            accountInfoItem.title = "${connectionState.loginAccount.name} (${connectionState.loginAccount.url})"
            accountInfoItem.isEnabled = true
        } else {
            accountInfoItem.title = "No account selected"
            accountInfoItem.isEnabled = false
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_switch_account -> {
                switchAccount()
                true
            }
            R.id.action_log_out -> {
                logOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun switchAccount() {
        // Clear the selected account but keep stored accounts
        storageProvider.clearSelectedAccount()

        // Reload the account list to reflect the cleared selection
        val accountList = storageProvider.loadHubAccounts()
        connectionService?.getConnectionStateProvider()?.dispatch(
            ConnectionAction.AccountListLoaded(accountList)
        )
    }

    private fun logOut() {
        // Remove only the currently selected account and its refresh token
        val currentAccounts = storageProvider.loadHubAccounts()
        currentAccounts.selectedAccount?.let { selectedAccountId ->
            storageProvider.removeAccount(selectedAccountId)
        }

        // Reload the updated account list and dispatch it
        val updatedAccountList = storageProvider.loadHubAccounts()
        connectionService?.getConnectionStateProvider()?.dispatch(
            ConnectionAction.AccountListLoaded(updatedAccountList)
        )

        // Navigate to login activity
        startActivity(Intent(this, LoginActivity::class.java))
    }
}