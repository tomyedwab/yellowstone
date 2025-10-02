package com.tomyedwab.yellowstone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.tomyedwab.yellowstone.databinding.ActivityLoginBinding
import com.tomyedwab.yellowstone.provider.connection.*
import com.tomyedwab.yellowstone.provider.storage.ConnectionStorageConnector
import com.tomyedwab.yellowstone.services.connection.ConnectionService

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var connectionService: ConnectionService? = null
    private var connectionStorageConnector: ConnectionStorageConnector? = null
    private var isBound = false
    private var currentState: HubConnectionState? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ConnectionService.ConnectionBinder
            connectionService = binder.getService()
            isBound = true

            // Initialize storage connector
            connectionService?.getConnectionStateProvider()?.let { stateProvider ->
                connectionStorageConnector = ConnectionStorageConnector(this@LoginActivity, stateProvider)
                connectionStorageConnector?.initialize()
            }

            setupObservers()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupTabListener()

        // Start and bind to the connection service
        val intent = Intent(this, ConnectionService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionStorageConnector?.cleanup()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }


    private fun setupObservers() {
        connectionService?.getConnectionStateProvider()?.connectionState?.observe(this) { state ->
            currentState = state
            updateUI(state)

            // Handle successful connection
            if (state is HubConnectionState.Connected) {
                // If this is a fresh login (no MainActivity in background), start MainActivity
                if (isTaskRoot) {
                    startActivity(Intent(this, MainActivity::class.java))
                }
                finish()
            }
        }
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val serverUrl = binding.serverUrlInput.text.toString()
            val username = binding.usernameInput.text.toString()
            val password = binding.passwordInput.text.toString()

            if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                val account = HubAccount(
                    id = username,
                    name = username,
                    url = serverUrl
                )
                connectionService?.getConnectionStateProvider()?.dispatch(
                    ConnectionAction.StartLogin(account, password)
                )
            }
        }
    }

    private fun setupTabListener() {
        // Create tabs programmatically
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Saved Accounts"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("New Account"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showSavedAccountsTab()
                    1 -> showNewAccountTab()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showSavedAccountsTab() {
        binding.savedAccountsContent.visibility = View.VISIBLE
        binding.newAccountContent.visibility = View.GONE
        populateSavedAccounts()
    }

    private fun showNewAccountTab() {
        binding.savedAccountsContent.visibility = View.GONE
        binding.newAccountContent.visibility = View.VISIBLE
    }

    private fun populateSavedAccounts() {
        val accountsList = binding.accountsList
        accountsList.removeAllViews()

        currentState?.accountList?.accounts?.forEach { account ->
            val accountCard = createAccountCard(account)
            accountsList.addView(accountCard)
        }
    }

    private fun createAccountCard(account: HubAccount): View {
        val cardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
            isClickable = true
            isFocusable = true
        }

        val inflater = LayoutInflater.from(this)
        val cardContent = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val nameText = TextView(this).apply {
            text = account.name
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val urlText = TextView(this).apply {
            text = account.url
            textSize = 14f
            setTextColor(getColor(android.R.color.darker_gray))
        }

        cardContent.addView(nameText)
        cardContent.addView(urlText)
        cardView.addView(cardContent)

        cardView.setOnClickListener {
            connectionService?.getConnectionStateProvider()?.dispatch(
                ConnectionAction.ConnectionSelected(account.id)
            )
        }

        return cardView
    }

    private fun updateUI(state: com.tomyedwab.yellowstone.provider.connection.HubConnectionState) {
        when (state) {
            is HubConnectionState.Uninitialized -> {
                binding.statusText.text = "Initializing..."
                binding.tabContainer.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                binding.errorText.visibility = View.GONE
                binding.loginButton.isEnabled = false
            }

            is HubConnectionState.WaitingForLogin -> {
                binding.loginButton.isEnabled = true

                // Show error if present
                state.connectionError?.let { error ->
                    binding.errorText.text = error
                    binding.errorText.visibility = View.VISIBLE
                } ?: run {
                    binding.errorText.visibility = View.GONE
                }

                val hasSavedAccounts = state.accountList?.accounts?.isNotEmpty() == true
                if (hasSavedAccounts) {
                    binding.statusText.text = "Select an account to connect"
                    binding.tabContainer.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE

                    // Enable/disable tabs based on available accounts
                    binding.tabLayout.getTabAt(0)?.view?.isEnabled = true
                    binding.tabLayout.getTabAt(0)?.view?.alpha = 1.0f

                    // Show saved accounts tab by default
                    binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
                    showSavedAccountsTab()
                } else {
                    binding.statusText.text = "Please log in"
                    binding.tabContainer.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE

                    // Disable saved accounts tab if no accounts
                    binding.tabLayout.getTabAt(0)?.view?.isEnabled = false
                    binding.tabLayout.getTabAt(0)?.view?.alpha = 0.5f

                    // Show new account tab by default
                    binding.tabLayout.selectTab(binding.tabLayout.getTabAt(1))
                    showNewAccountTab()
                }
            }

            is HubConnectionState.Connecting -> {
                binding.statusText.text = "Connecting..."
                binding.tabContainer.visibility = View.VISIBLE
                binding.progressBar.visibility = View.VISIBLE
                binding.loginButton.isEnabled = false
                binding.errorText.visibility = View.GONE
            }

            is HubConnectionState.Connected -> {
                binding.statusText.text = "Connected successfully!"
                binding.progressBar.visibility = View.GONE
                binding.errorText.visibility = View.GONE
            }
        }

    }
}
