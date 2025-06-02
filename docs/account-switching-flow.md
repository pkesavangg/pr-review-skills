# Account Switching Flow

This document describes the account switching feature in the MeApp project, including user interactions and system processes.

## Overview

The account switching feature allows users to manage multiple accounts within the app, including:

-   Adding new accounts
-   Switching between accounts
-   Managing account settings
-   Handling account sessions
-   Removing accounts

## User Flow

The following sequence diagram illustrates the user and system interactions for the account switching feature:

```mermaid
sequenceDiagram
    participant User
    participant App
    participant AuthService
    participant AccountService
    User->>App: Open App
    App->>User: Show Launch Screen
    alt First Time Launch
        App->>User: Show Get Started Screen
        User->>App: Tap "Get Started"
        App->>User: Show Sign In / Sign Up options
    end
    alt Login Flow
        User->>App: Tap Sign In
        App->>AuthService: Authenticate(User Credentials)
        AuthService-->>App: Return Auth Token
        App->>AccountService: Store Account
        AccountService-->>App: Success
        App->>User: Redirect to Main Screen
    end
    alt Signup Flow
        User->>App: Tap Sign Up
        App->>AuthService: Register New Account
        AuthService-->>App: Return Auth Token
        App->>AccountService: Store Account
        AccountService-->>App: Success
        App->>User: Redirect to Main Screen
    end
    alt View Existing Accounts
        User->>App: Open App After Initial Login
        App->>AccountService: Fetch Stored Accounts
        AccountService-->>App: Return Account List
        App->>User: Account Selection popup via account settings page
    end
    alt Switch Account
        User->>App: Tap on an Account
        App->>AccountService: Switch Active Account
        AccountService-->>App: Success
        App->>User: Load Dashboard for Selected Account
    end
    alt Add Another Account
        User->>App: Tap "Add Another Account"
        App->>User: Show Sign In / Sign Up options
        User->>App: Sign In with Another Account
        App->>AuthService: Authenticate(New Account)
        AuthService-->>App: Return Token
        App->>AccountService: Add to Account List
        AccountService-->>App: Success
        App->>User: Go to added account's Dashboard
    end
    alt Max Accounts Reached
        User->>App: Try to Add Account
        App->>AccountService: Check Max Account Limit
        AccountService-->>App: Max Limit Reached
        App->>User: Show Limit Reached Alert
    end
    alt Remove Account
        User->>App: Tap "Remove Account"
        App->>AccountService: Remove Account
        AccountService-->>App: Return Updated List
        App->>User: Show Updated Account List
    end
    alt Auto Logout of Accounts
        App->>AccountService: Periodic Session Check
        AccountService-->>App: Detect Invalid Tokens
        App->>User: Redirect to Account Selection Screen
    end
    alt Logout Current Account
        User->>App: Tap Logout
        App->>AccountService: Logout Active Account
        AccountService-->>App: Remove Token
        App->>User: Redirect to Account Selection
    end
    alt Logout All Accounts
        User->>App: Tap "Logout All"
        App->>AccountService: Clear All Accounts
        AccountService-->>App: Success
        App->>User: Redirect to Launch Screen
    end
```

## Key Features

1. **First Time Launch**

    - Shows Get Started screen
    - Guides user through initial sign-in/sign-up process

2. **Account Management**

    - Add multiple accounts
    - Switch between accounts
    - Remove accounts
    - View account list

3. **Session Management**

    - Automatic session validation
    - Token management
    - Auto logout for expired sessions

4. **Security**

    - Secure token storage
    - Session validation
    - Account isolation

5. **User Experience**
    - Smooth account switching
    - Clear account selection UI
    - Session status indicators

## Implementation Notes

1. **Account Storage**

    - Accounts are stored in the local database
    - Each account maintains its own session state
    - Account data is isolated between accounts

2. **Session Management**

    - Regular token validation
    - Automatic session refresh
    - Secure token storage

3. **UI/UX Considerations**

    - Clear account selection interface
    - Smooth transitions between accounts
    - Clear session status indicators

4. **Security Considerations**
    - Secure token storage
    - Account data isolation
    - Session validation
    - Secure account removal
