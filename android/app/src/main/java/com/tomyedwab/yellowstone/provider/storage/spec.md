Storage Provider
================

This provider exposes utilities for storing application data securely, including
login accounts and authentication tokens.

## Implementation

The ConnectionStorageProvider class is implemented using Android's EncryptedSharedPreferences
with AES256 encryption to securely store sensitive authentication data.

## Public Methods

### loadHubAccounts(): HubAccountList
Loads all stored HubAccounts from secure storage at startup, including the last 
selected account ID. Returns a HubAccountList containing the accounts and selected 
account reference.

### saveHubAccount(hubUrl: String, username: String, refreshToken: String): HubAccount
Saves or updates a HubAccount after successful login. If an account with the same
URL and username already exists, it updates the refresh token. Otherwise, creates
a new account with a unique UUID. The account is automatically set as the selected
account and returns the HubAccount object.

### setSelectedAccount(accountId: String)
Sets the specified account as the selected account for fast switching between
stored accounts. Only sets if the account ID exists in storage.

### clearSelectedAccount()
Clears the selected account, typically called when login fails or user logs out.

### removeAccount(accountId: String)
Removes an account from storage. If the removed account was selected, clears
the selection.

## Data Storage

The provider uses encrypted storage with the following keys:
- `hub_accounts`: JSON array of HubAccount objects
- `selected_account_id`: ID of the currently selected account

## ConnectionStorageConnector

The ConnectionStorageConnector bridges the storage provider with the connection state management system. It is initialized in LoginActivity (the launcher activity) with a ConnectionStateProvider instance.

### Initialization Flow
1. **App startup**: LoginActivity launches and binds to ConnectionService
2. **Connector initialization**: Creates ConnectionStorageConnector and calls `initialize()`
3. **Load stored data**: Dispatches `AccountListLoaded` action with stored accounts
4. **Auto-selection**: If a selected account exists, dispatches `ConnectionSelected` action
5. **Auto-reconnection**: Selected account with refresh token triggers automatic connection attempt

### State Observation
The connector observes connection state changes and automatically:
- **Clear selection on new login**: When user starts manual login, clears stored selection
- **Save successful connections**: When connection succeeds, updates stored account with new refresh token and sets as selected

### Lifecycle Management
- **Initialize**: Call `initialize()` after ConnectionService is bound
- **Cleanup**: Call `cleanup()` in activity's `onDestroy()` to prevent memory leaks

## Behavior

- When a new account is successfully logged into, it is automatically added to the 
  list of accounts and set as the selected account
- Existing accounts are updated with new refresh tokens rather than creating duplicates
- Account selection is cleared when starting a new manual login
- Stored accounts with refresh tokens enable automatic reconnection at app startup
- Enables fast re-authentication when opening the app and fast switching between accounts

## Data Model

A HubAccount includes:
- `id`: Unique UUID string identifier
- `name`: Username for the account
- `url`: URL of the hub to connect to
- `refreshToken`: Last successfully used refresh token (nullable)
