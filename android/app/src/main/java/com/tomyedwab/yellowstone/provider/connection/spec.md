Connection State Provider
=========================

This is a library meant to integrate with a backend providing authentication,
data loading, and event sourcing capabilities called Yesterday. The Yesterday
API allows a client to log in, obtain credentials, upload and install backend
components, poll for the latest event state for each backend component, fetch
data from the components, and publish new events.

Provider implementation
=======================

The provider is implemented using a "reducer" pattern. It defines a set of
actions and a reducer function to manage the state of the connection. UI
components can watch the state of the connection and dispatch actions to update
it.

The provider state only tracks the current state of the connection, but does not
take any actions itself to change the state, for example by issuing requests to
the backend. That is handled by a separate component.

Connection States
================

The connection can be in one of the following states:

- `NOT_INITIALIZED`: We have not yet loaded the local list of hub accounts.
- `NO_SELECTION`: There are hub accounts available, but the user has not selected one.
- `LOGIN`: There are no hub accounts or the user has selected one that is not logged in.
- `CONNECTING`: We are connecting to a hub account.
- `CONNECTED`: We are now connected to a hub account.

State Data Structure
===================

The `HubConnectionState` contains:

- `state`: Current connection state (enum)
- `accountList`: List of previously-used hub accounts with refresh tokens
- `loginAccount`: The account currently being used for login/connection
- `loginPassword`: Password for new login attempts
- `refreshToken`: Refresh token for automatic reconnection
- `accessToken`: Current access token once successfully logged in
- `backendComponentIDs`: Current backend instance IDs for components
- `backendEventIDs`: Current event IDs for each backend instance
- `connectionError`: Error message from failed connection attempts
- `pendingEvents`: List of events waiting to be published to the backend

Actions
=======

The following actions can be dispatched to update the connection state:

- `AccountListLoaded(accountList)`: Loaded the list of available hub accounts
- `ConnectionSelected(selectedAccount)`: User selected a specific account to connect to
- `StartLogin(account, password)`: Begin login process with username/password
- `StartConnection(account, refreshToken)`: Begin connection using stored refresh token
- `ReceivedAccessToken(accessToken)`: Received access token from successful authentication
- `MappedComponentIDs(componentIDs)`: Received mapping of component IDs from backend
- `ConnectionFailed(error)`: Connection attempt failed with error message
- `ConnectionSucceeded(eventIDs)`: Successfully connected and received initial event IDs
- `EventsUpdated(eventIDs)`: Received updated event IDs from backend polling
- `PublishEvent(event)`: Queue an event for publishing to the backend
- `EventPublished(clientId)`: Remove successfully published event from pending list

Event Structure
===============

Events to be published have the following structure:

- `clientId`: Randomly-generated unique identifier for the event
- `type`: String identifying the event type
- `timestamp`: Unix timestamp when the event was created
- `data`: Map containing additional fields specific to the event type
