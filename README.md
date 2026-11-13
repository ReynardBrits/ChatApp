# ChatApp

ChatApp is a messaging application created for my Hybrid Mobile Application Development project. Users can register an account, log in, choose another user and exchange private messages. They can also take a photo using their device camera and send it in a conversation.

The main application is written in Java for Android. A separate Java server handles accounts, messages, photos and live updates. The project also includes a browser version for testing.

**Student:** Reynard Brits  
**Module:** ITMDA4-34 — Hybrid Mobile Application Development

## Main features

- Account registration and login.
- Checks for empty fields, invalid usernames and mismatched passwords.
- A Home screen showing conversations and recent messages.
- A user picker for starting a new conversation.
- Private messages with different layouts for sent and received messages.
- Camera photo sharing.
- Saved conversation history.
- Unread message counts.
- Android push notifications.
- Logout and protected screens that require a valid session.

## Try the browser version

The browser version is available here:

[Open ChatApp](https://chatapp-l3zc.onrender.com)

1. Open the link.
2. Select **Create an account** and register a username and password.
3. Log in with those details.
4. Start a conversation with another registered user.
5. Send a message or try the camera feature.

To test communication between two accounts, use two browsers, a normal and private browser window, or the browser version together with the Android app.

Allow camera access when testing photo capture.

## Run the Android app

You will need:

- Android Studio.
- Android SDK 37.
- An Android emulator or a phone running Android 8.0 or later.
- Internet access.

For push notification testing, use a phone or emulator with Google Play services.

### Setup

1. Clone or download this repository.
2. Open the main **ChatApp** folder in Android Studio.
3. Allow Gradle to finish syncing.
4. Install any missing SDK components requested by Android Studio.
5. Start an emulator or connect an Android phone with USB debugging enabled.
6. Select the **app** run configuration and press **Run**.
7. Register an account, then log in.
8. Allow notifications when prompted if you want to test them.

The Android app is currently configured to connect to the hosted server:

`https://chatapp-l3zc.onrender.com/`

Its WebSocket connection uses:

`wss://chatapp-l3zc.onrender.com/ws/chat`

You do not need to start a server on your computer when using this configuration.

The connection settings are stored in:

`app/src/main/java/com/example/chatapp/network/ServerConfig.java`

The Android app and browser version use the same accounts when connected to the same server.

## Build an APK

Open a terminal in the main ChatApp folder and run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK will be created at:

`app/build/outputs/apk/debug/app-debug.apk`

This APK can be installed on a compatible Android device for testing.

## Run the server locally

A local copy of the server can also be used for development and browser testing.

You will need Maven and a JDK capable of compiling Java 17.

Open PowerShell in the main ChatApp folder and run:

```powershell
Set-Location .\server
mvn compile exec:java
```

Leave this terminal open while using the local server.

By default, it runs on port **8081**. Open the following address to check it:

[Local server health check](http://localhost:8081/api/health)

The expected response is:

```json
{"status":"ok"}
```

The local browser version can be opened at:

[Local ChatApp](http://localhost:8081)

Starting this server does not change the Android app's configured address. The Android app will continue using Render unless its connection settings are changed.

The local and hosted servers have separate databases. An account registered on one server is not automatically available on the other.

## Push notifications

The Android app uses Firebase Cloud Messaging to receive push notifications.

When testing the hosted version, allow ChatApp to show notifications on the receiving device. Put the app in the background and send a message to that account from another user.

For a separately configured local server, push notifications require a Firebase service-account JSON file from the same Firebase project used by the Android app.

Set its path in the server's PowerShell terminal before starting the server:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\path\to\firebase-service-account.json"
mvn compile exec:java
```

Replace the example path with the actual file location. Keep the service-account file outside the repository.

If this environment variable is not set, the local server starts with Firebase notifications disabled.

## Test the main functions

1. Register two different accounts.
2. Log in to each account on a separate device, emulator or browser session.
3. Start a conversation between them.
4. Send a message from each side and check that both messages appear.
5. Take a photo and check that the other account receives it.
6. Return to Home and check the conversation preview.
7. Reopen the conversation and check that its history is still available.
8. Send a message while the receiving Android app is in the background and check for a notification.
9. Log out and confirm that the protected screens require login again.

Both accounts must be connected to the same server during these tests.

## How the project works

The Android screens use XML layouts. Activities handle the screen controls and navigation, while ViewModels help manage the displayed data. RecyclerView adapters display the conversation and message lists.

Room stores a local copy of conversation data on the Android device. The Java server stores the shared accounts, messages and uploaded photos. This allows different devices to access the same conversations.

HTTP requests are used for actions such as registration, login and loading data. WebSockets provide live message updates. The server uses Java Servlets and JSR 356 WebSocket endpoints, while the browser client uses HTML, CSS and JavaScript.

## Project structure

| Location | What it contains |
| --- | --- |
| `app/build.gradle.kts` | Android dependencies and build settings |
| `app/src/main/java/com/example/chatapp/activities/` | Activities and ViewModels for the main screens |
| `app/src/main/java/com/example/chatapp/database/` | Room database helper, User and Message entities, and DAOs |
| `app/src/main/java/com/example/chatapp/adapters/` | Adapters for conversation and message lists |
| `app/src/main/java/com/example/chatapp/network/` | Server settings, HTTP requests and WebSocket communication |
| `app/src/main/java/com/example/chatapp/notifications/` | Notification handling and push registration |
| `app/src/main/java/com/example/chatapp/utils/` | Shared constants, session handling and other utilities |
| `app/src/main/res/layout/` | XML layouts for screens and list items |
| `server/pom.xml` | Server dependencies and Maven build settings |
| `server/src/main/java/com/example/chatapp/server/` | Server startup, Servlets, WebSocket endpoints and database access |
| `server/src/main/resources/web/` | Browser client files |

## Stored data

When the server is started locally using the commands above, its database and uploaded photos are stored under `server/data/`.

=======
Restarting the server or the Android app process requires signing in again.
Registered accounts, messages, and uploaded photos remain in server storage.
>>>>>>> a86041f4e5354296f427c997052e43ac5047c5a6
