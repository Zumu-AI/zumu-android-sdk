package com.zumu.translator

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Date

/**
 * Zumu Driver Translator SDK for Android
 * Provides real-time translation for driver-passenger conversations
 */
class ZumuTranslator(
    private val apiKey: String,
    private val baseURL: String = "https://translator.zumu.ai"
) {

    // MARK: - State Properties

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<TranslationMessage>>(emptyList())
    val messages: StateFlow<List<TranslationMessage>> = _messages.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // MARK: - Private Properties

    private var currentSession: TranslationSession? = null
    private val httpClient = OkHttpClient()
    private var isStarting: Boolean = false

    // MARK: - Session Management

    /**
     * Start a new translation session
     * @param config Session configuration
     * @param context Android context for microphone access
     * @return Started translation session
     * @throws ZumuException if session creation fails
     */
    suspend fun startSession(config: SessionConfig, context: Context): TranslationSession {
        // Prevent race conditions from double-clicks
        if (isStarting) {
            throw ZumuException.InvalidState("Session start already in progress")
        }

        if (_state.value != SessionState.Idle) {
            throw ZumuException.InvalidState("Cannot start session while in state: ${_state.value}")
        }

        isStarting = true
        _state.value = SessionState.Connecting
        var createdSession: TranslationSession? = null

        try {
            // Step 1: Create session on backend
            val session = createBackendSession(config)
            createdSession = session
            currentSession = session

            // Step 2: Start conversation
            val conversationData = startConversation(session.id)

            // TODO: Initialize WebSocket connection with conversationData.signedUrl
            // TODO: Set up audio capture

            _state.value = SessionState.Active
            return session

        } catch (e: Exception) {
            // Clean up partial session if created
            createdSession?.let {
                try {
                    updateSessionStatus(it.id, "failed")
                } catch (cleanup: Exception) {
                    // Ignore cleanup errors
                }
            }

            // Reset to idle to allow retry (enterprise-grade error recovery)
            _state.value = SessionState.Idle
            currentSession = null
            throw ZumuException.ApiError(e.message ?: "Failed to start session")
        } finally {
            isStarting = false
        }
    }

    /**
     * Reset error state to allow retry
     * Call this when you want to retry after an error
     */
    fun resetState() {
        _state.value = SessionState.Idle
        currentSession = null
        _messages.value = emptyList()
        isStarting = false
    }

    /**
     * End the current translation session
     */
    suspend fun endSession() {
        val session = currentSession ?: return

        _state.value = SessionState.Ending

        try {
            // Update backend session status
            updateSessionStatus(session.id, "ended")

            // Reset state
            currentSession = null
            _messages.value = emptyList()
            _state.value = SessionState.Idle

        } catch (e: Exception) {
            _state.value = SessionState.Error(e.message ?: "Failed to end session")
        }
    }

    /**
     * Send a text message in the conversation
     * @param text Message text
     */
    suspend fun sendMessage(text: String) {
        if (currentSession == null) {
            throw ZumuException.InvalidState("No active conversation")
        }

        // TODO: Send message via WebSocket
    }

    /**
     * Toggle microphone mute
     */
    fun toggleMute() {
        if (currentSession == null) return

        val newMutedState = !_isMuted.value
        // TODO: Implement actual mute/unmute logic
        _isMuted.value = newMutedState
    }

    /**
     * Get current session ID
     * @return Session ID or null if no active session
     */
    fun getSessionId(): String? = currentSession?.id

    // MARK: - Private Methods

    private suspend fun createBackendSession(config: SessionConfig): TranslationSession {
        val json = JSONObject().apply {
            put("driver_name", config.driverName)
            put("driver_language", config.driverLanguage)
            put("passenger_name", config.passengerName)
            config.passengerLanguage?.let { put("passenger_language", it) }
            put("trip_id", config.tripId)
            config.pickupLocation?.let { put("pickup_location", it) }
            config.dropoffLocation?.let { put("dropoff_location", it) }
            put("client_info", JSONObject().apply {
                put("platform", "Android")
                put("sdk_version", "1.0.0")
                put("device", android.os.Build.MODEL)
            })
        }

        val request = Request.Builder()
            .url("$baseURL/api/sessions")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No response body"
            throw IOException("Failed to create session (HTTP ${response.code}): $errorBody")
        }

        val responseData = JSONObject(response.body?.string() ?: "{}")

        return TranslationSession(
            id = responseData.getString("session_id"),
            status = responseData.getString("status"),
            createdAt = responseData.getString("created_at")
        )
    }

    private suspend fun startConversation(sessionId: String): ConversationData {
        val json = JSONObject().apply {
            put("session_id", sessionId)
        }

        val request = Request.Builder()
            .url("$baseURL/api/conversations/start")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No response body"
            throw IOException("Failed to start conversation (HTTP ${response.code}): $errorBody")
        }

        val responseData = JSONObject(response.body?.string() ?: "{}")

        return ConversationData(
            conversationId = responseData.getString("conversation_id"),
            signedUrl = responseData.getString("signed_url"),
            agentId = responseData.getString("agent_id")
        )
    }

    private suspend fun updateSessionStatus(sessionId: String, status: String) {
        val json = JSONObject().apply {
            put("status", status)
        }

        val request = Request.Builder()
            .url("$baseURL/api/sessions/$sessionId")
            .patch(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No response body"
            throw IOException("Failed to update session (HTTP ${response.code}): $errorBody")
        }
    }
}

// MARK: - Session State

sealed class SessionState {
    object Idle : SessionState()
    object Connecting : SessionState()
    object Active : SessionState()
    object Disconnected : SessionState()
    object Ending : SessionState()
    data class Error(val message: String) : SessionState()
}

// MARK: - Models

data class SessionConfig(
    val driverName: String,
    val driverLanguage: String,
    val passengerName: String,
    val passengerLanguage: String? = null,
    val tripId: String,
    val pickupLocation: String? = null,
    val dropoffLocation: String? = null
)

data class TranslationSession(
    val id: String,
    val status: String,
    val createdAt: String
)

data class TranslationMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Date
)

data class ConversationData(
    val conversationId: String,
    val signedUrl: String,
    val agentId: String
)

// MARK: - Errors

sealed class ZumuException(message: String) : Exception(message) {
    class InvalidState(message: String) : ZumuException(message)
    class ApiError(message: String) : ZumuException(message)
    class NetworkError(message: String) : ZumuException(message)
}
