# Zumu Driver Translator - Android SDK

Enterprise-grade real-time translation SDK for driver-passenger conversations.

## Features

- 🎯 **Real-time Translation**: Instant voice-to-voice translation
- 🔐 **Secure Authentication**: API key-based authentication
- 📱 **Jetpack Compose Integration**: Reactive state management with Kotlin Flows
- 🎤 **Microphone Control**: Built-in mute/unmute functionality
- 💬 **Text Messaging**: Send text messages alongside voice
- 📊 **Session Management**: Track and manage conversation sessions
- 🔄 **Coroutines Support**: Modern async/await patterns

## Requirements

- Android 8.0 (API level 26) or higher
- Kotlin 1.8+

## Installation

### Local Module Integration

1. Clone the repository:
```bash
git clone https://github.com/Zumu-AI/zumu-android-sdk.git
```

2. Include in your project's `settings.gradle.kts`:
```kotlin
include(":zumu-translator")
project(":zumu-translator").projectDir = file("path/to/zumu-android-sdk/zumu-translator")
```

3. Add dependency in your app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":zumu-translator"))
}
```

**Note**: This SDK requires minimal external dependencies. Everything is handled through Zumu's secure infrastructure.

## Quick Start

### 1. Get Your API Key

1. Log in to the [Zumu Dashboard](https://your-domain.com/dashboard)
2. Navigate to **API Keys**
3. Click **Create API Key**
4. Copy your key (format: `zumu_xxxxxxxxxxxx`)

⚠️ **Important**: Never commit API keys to your repository. Store them securely.

### 2. Initialize the SDK

```kotlin
import com.zumu.translator.ZumuTranslator

val translator = ZumuTranslator(
    apiKey = "zumu_your_api_key_here"
)
```

### 3. Start a Translation Session

```kotlin
import kotlinx.coroutines.launch

val config = SessionConfig(
    driverName = "John Doe",
    driverLanguage = "English",
    passengerName = "María García",
    passengerLanguage = "Spanish",
    tripId = "TRIP-12345",
    pickupLocation = "123 Main St",
    dropoffLocation = "456 Oak Ave"
)

lifecycleScope.launch {
    try {
        val session = translator.startSession(config, context)
        println("Session started: ${session.id}")
    } catch (e: Exception) {
        println("Error starting session: ${e.message}")
    }
}
```

### 4. Build Your UI (Jetpack Compose)

```kotlin
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

@Composable
fun TranslatorScreen(translator: ZumuTranslator) {
    val state by translator.state.collectAsState()
    val messages by translator.messages.collectAsState()
    val isMuted by translator.isMuted.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Status indicator
        StatusCard(state = state)

        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageRow(message = message)
            }
        }

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { translator.toggleMute() }) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute"
                )
            }

            Button(
                onClick = {
                    lifecycleScope.launch {
                        translator.endSession()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("End Session")
            }
        }
    }
}
```

## Complete Example

```kotlin
package com.example.driverapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.zumu.translator.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var translator: ZumuTranslator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        translator = ZumuTranslator(
            apiKey = BuildConfig.ZUMU_API_KEY
        )

        setContent {
            ZumuTheme {
                DriverScreen(translator = translator, context = this)
            }
        }
    }
}

@Composable
fun DriverScreen(translator: ZumuTranslator, context: Context) {
    val state by translator.state.collectAsState()
    val messages by translator.messages.collectAsState()
    val isMuted by translator.isMuted.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zumu Translator") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Session status
            SessionStatusCard(state = state)

            // Start button or active conversation
            when (state) {
                SessionState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    startNewSession(translator, context)
                                }
                            }
                        ) {
                            Text("Start Translation")
                        }
                    }
                }
                SessionState.Active -> {
                    ConversationView(
                        translator = translator,
                        messages = messages,
                        isMuted = isMuted
                    )
                }
                else -> {
                    // Loading or error state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationView(
    translator: ZumuTranslator,
    messages: List<TranslationMessage>,
    isMuted: Boolean
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
        }

        // Controls
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Mute button
                FloatingActionButton(
                    onClick = { translator.toggleMute() },
                    containerColor = if (isMuted) Color.Red else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }

                // End call button
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            translator.endSession()
                        }
                    },
                    containerColor = Color.Red
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Session"
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: TranslationMessage) {
    val isUser = message.role == "user"
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = dateFormat.format(message.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}

@Composable
fun SessionStatusCard(state: SessionState) {
    val (color, text) = when (state) {
        SessionState.Idle -> Color.Gray to "Ready"
        SessionState.Connecting -> Color(0xFFFFA500) to "Connecting..."
        SessionState.Active -> Color.Green to "Active"
        SessionState.Disconnected -> Color.Red to "Disconnected"
        SessionState.Ending -> Color(0xFFFFA500) to "Ending..."
        is SessionState.Error -> Color.Red to "Error: ${state.message}"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private suspend fun startNewSession(translator: ZumuTranslator, context: Context) {
    val config = SessionConfig(
        driverName = "John Doe", // Get from user preferences
        driverLanguage = "English",
        passengerName = "Passenger",
        tripId = UUID.randomUUID().toString()
    )

    try {
        translator.startSession(config, context)
    } catch (e: Exception) {
        // Handle error
        println("Failed to start session: ${e.message}")
    }
}
```

## API Reference

### ZumuTranslator

Main SDK class for managing translation sessions.

#### Properties

```kotlin
val state: StateFlow<SessionState>              // Current session state
val messages: StateFlow<List<TranslationMessage>>  // Conversation messages
val isMuted: StateFlow<Boolean>                 // Microphone mute state
```

#### Methods

```kotlin
// Initialize translator
ZumuTranslator(apiKey: String, baseURL: String = "https://your-domain.com/api")

// Start a new session
suspend fun startSession(config: SessionConfig, context: Context): TranslationSession

// End current session
suspend fun endSession()

// Send text message
suspend fun sendMessage(text: String)

// Toggle microphone mute
fun toggleMute()

// Get current session ID
fun getSessionId(): String?
```

### SessionConfig

Configuration for starting a translation session.

```kotlin
data class SessionConfig(
    val driverName: String,         // Required: Driver's full name
    val driverLanguage: String,     // Required: Driver's language
    val passengerName: String,      // Required: Passenger's name
    val passengerLanguage: String?, // Optional: Passenger's language
    val tripId: String,             // Required: Unique trip identifier
    val pickupLocation: String?,    // Optional: Pickup address
    val dropoffLocation: String?    // Optional: Dropoff address
)
```

### SessionState

```kotlin
sealed class SessionState {
    object Idle          // No active session
    object Connecting    // Establishing connection
    object Active        // Session in progress
    object Disconnected  // Connection lost
    object Ending        // Session terminating
    data class Error(val message: String)  // Error occurred
}
```

## Permissions

Add the following to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

Request microphone permission at runtime:

```kotlin
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, can start session
        } else {
            // Permission denied
        }
    }

    fun requestMicrophonePermission() {
        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
}
```

## Best Practices

### 1. Secure API Key Storage

```kotlin
// ✅ Good: Use BuildConfig or encrypted storage
val apiKey = BuildConfig.ZUMU_API_KEY

// ❌ Bad: Hardcode in source
val apiKey = "zumu_abc123..." // NEVER DO THIS
```

Add to `build.gradle.kts`:

```kotlin
android {
    buildTypes {
        debug {
            buildConfigField("String", "ZUMU_API_KEY", "\"${project.findProperty("ZUMU_API_KEY")}\"")
        }
    }
}
```

### 2. Handle Connection Errors

```kotlin
lifecycleScope.launch {
    translator.state.collect { state ->
        when (state) {
            is SessionState.Error -> {
                // Show error to user
                showErrorDialog(state.message)

                // Attempt reconnection
                delay(2000)
                try {
                    translator.startSession(lastConfig, context)
                } catch (e: Exception) {
                    // Handle reconnection failure
                }
            }
            else -> {}
        }
    }
}
```

### 3. Manage Session Lifecycle

```kotlin
override fun onPause() {
    super.onPause()
    // End session when app goes to background
    lifecycleScope.launch {
        translator.endSession()
    }
}
```

## Troubleshooting

### "Invalid API key" Error
- Verify your API key is correct
- Check that the key is active in the dashboard
- Ensure the key hasn't expired

### "Failed to create session" Error
- Check your network connection
- Verify all required fields in `SessionConfig`
- Check dashboard for quota limits

### No Audio
- Verify microphone permissions
- Check that device isn't muted
- Ensure Bluetooth devices are connected properly

## ProGuard Rules

If using ProGuard, add these rules:

```proguard
-keep class com.zumu.translator.** { *; }
```

## Support

- Documentation: https://docs.your-domain.com
- Dashboard: https://your-domain.com/dashboard
- Email: support@your-domain.com

## License

Copyright © 2025 Zumu. All rights reserved.
