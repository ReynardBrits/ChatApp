# ChatApp

An Android chat application written in Java, with a separate Java server.

Features include registration, login, user selection, private conversations,
camera photo sharing, and saved conversation history. WebSockets deliver
live message updates.

## Requirements

- Android Studio with Android SDK 37 installed.
- Two Android emulators for testing between users.
- Maven and a JDK capable of compiling Java 17.
- Internet access for the initial dependency downloads.

## Start the server

Open a PowerShell terminal in the ChatApp project folder:

```powershell
Set-Location .\server
mvn compile exec:java
```

Leave this terminal running while using the app.

Check the server on the computer at:

http://localhost:8081/api/health

The expected response is:

```json
{"status":"ok"}
```

## Run the Android app

1. Open the ChatApp project in Android Studio.
2. Allow Gradle synchronization to finish.
3. Start an emulator and run the `app` configuration.
4. Allow Nearby devices access if requested.
5. Register an account, then log in.

The app connects to `http://10.0.2.2:8081/`. This configuration is for
Android emulators connecting to the server on the development computer.

Connection settings are in:

`app/src/main/java/com/example/chatapp/network/ServerConfig.java`

## Test communication between two users

1. Run the app on two emulators.
2. Register a different account on each emulator.
3. Log in to both accounts.
4. Select the other user on each Home screen and open Conversations.
5. Send a text message and confirm it appears on both devices.
6. Tap Camera, take a photo, confirm it, and check the receiving device.

Accounts must be registered when starting with a new server database.

## Build the Android app

In a second terminal, from the ChatApp project folder:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## Project structure

| Location | Purpose |
| --- | --- |
| `app/build.gradle.kts` | Android dependencies and build configuration |
| `app/src/main/java/com/example/chatapp/activities/` | Login, registration, Home, and conversation activities |
| `app/src/main/java/com/example/chatapp/database/` | Room database helper, User and Message models, and DAOs |
| `app/src/main/java/com/example/chatapp/adapters/MessageAdapter.java` | Displays text and photo messages |
| `app/src/main/java/com/example/chatapp/utils/Constants.java` | Shared application constants |
| `app/src/main/java/com/example/chatapp/network/` | HTTP requests, WebSocket client, and server configuration |
| `app/src/main/res/layout/` | Activity layouts and message row layout |
| `server/pom.xml` | Maven server dependencies and build configuration |
| `server/src/main/java/com/example/chatapp/server/` | Server startup, servlets, authentication, database access, and WebSocket endpoint |

## Data and sessions

The server stores its database and uploaded photos under `server/data/`.
Android Room databases cache conversation data separately for each account.

Restarting the server or the Android app process requires signing in again.
Registered accounts, messages, and uploaded photos remain in server storage.

## Current limitations

This setup uses a local development server and the Android debug build.
The server must remain running for communication between devices.

Delivery between emulators can sometimes be delayed. Performance
optimization is pending.