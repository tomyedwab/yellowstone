# Login Page Specification

**Route**: `/login`

## Overview
Authentication page for user login to the Yellowstone notes application with support for saved accounts and new account creation.

## Layout

### General Structure
- **Status Display**: Connection status and instructions
- **Progress Indicator**: Loading spinner during connection states
- **Tab Layout**: Two-tab interface for account selection
- **Content Areas**: Saved accounts list and new account form

### Tab Interface
1. **Saved Accounts Tab**
   - Shows previously used accounts
   - Enabled only when saved accounts exist
   - Disabled (50% opacity) when no accounts available

2. **New Account Tab**
   - Manual login form for new connections
   - Always available
   - Default tab when no saved accounts exist

### Saved Accounts Content
- **Account Cards**: Material design cards for each saved account
  - Account name (bold, 18pt font)
  - Server URL (14pt, gray text)
  - Card elevation and rounded corners (8px radius)
  - Tap to select and connect
- **Dynamic Population**: Updates based on stored account list

### New Account Form
1. **Server URL Field**
   - Input type: Text with border
   - Label: "Server URL"
   - Validation: Required field (non-blank)

2. **Username Field**
   - Input type: Text with border
   - Label: "Username"
   - Validation: Required field (non-blank)

3. **Password Field**
   - Input type: Password (masked text)
   - Label: "Password"
   - Validation: Required field (non-blank)

4. **Login Button**
   - Text: "Login"
   - Full width styling
   - Disabled during connection process

## Connection States

### Visual State Management
1. **NOT_INITIALIZED**
   - Status: "Initializing..."
   - Tabs hidden, progress bar visible

2. **NO_SELECTION**
   - With saved accounts: "Select an account to connect"
   - Without saved accounts: "Please log in"
   - Shows appropriate default tab

3. **LOGIN**
   - Status: "Please log in"
   - Shows new account tab
   - Login button enabled

4. **CONNECTING**
   - Status: "Connecting..."
   - Progress bar visible
   - Login button disabled

5. **CONNECTED**
   - Status: "Connected successfully!"
   - Transitions to main application

## Navigation Actions
- **Successful Connection**: Starts MainActivity and finishes login activity
- **Account Selection**: Dispatches ConnectionSelected action
- **New Login**: Dispatches StartLogin action with account details

## API Integration
- **Connection Service**: Android service managing connection state
- **Account Storage**: Persistent storage of account credentials
- **State Observation**: LiveData observation for UI updates

## Event Publications
- **ConnectionAction.StartLogin**: Initiates new account login
  - Parameters: HubAccount (id, name, url), password
- **ConnectionAction.ConnectionSelected**: Selects saved account
  - Parameters: account ID

## User Interactions

### Account Management
1. **Saved Account Selection**:
   - Tap account card to connect
   - Automatic connection attempt using stored credentials

2. **New Account Creation**:
   - Fill all required fields (server URL, username, password)
   - Tap login button or submit form
   - Account automatically saved on successful connection

3. **Tab Switching**:
   - Toggle between saved accounts and new account forms
   - Dynamic tab enablement based on available accounts

### Error Handling
- **Connection Errors**: Display error message below status
- **Form Validation**: Require all fields before submission
- **Network Issues**: Show appropriate error states

## Service Integration
- **ConnectionService**: Background service for managing connections
- **ServiceConnection**: Bound service pattern for communication
- **ConnectionStorageConnector**: Handles account persistence
- **ComponentAssets**: Initializes connection components

## Visual Theme
- **Material Design**: Cards, tabs, and form elements
- **Card Styling**: 4dp elevation, 8dp radius, 16dp bottom margin
- **Typography**: Bold names (18pt), gray URLs (14pt)
- **Interactive Elements**: Proper focus and click feedback