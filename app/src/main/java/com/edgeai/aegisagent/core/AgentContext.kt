package com.edgeai.aegisagent.core

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe container representing the shared environment state,
 * conversation history, and debugging trace logs across all agents.
 */
class AgentContext {

    // Flag to toggle between local fine-tuned Ollama mode and simulated mode
    @Volatile var isSimulationMode: Boolean = false

    // Global simulated hardware states
    private val deviceState = ConcurrentHashMap<String, Any>()

    // Thread-safe collection for conversational history
    val history = CopyOnWriteArrayList<Message>()

    // Step-by-step logs of which agents ran and which tools were called
    val executionTrace = CopyOnWriteArrayList<String>()

    init {
        // Initialize with default production states
        deviceState["volume"] = 50
        deviceState["brightness"] = 70
        deviceState["flashlight"] = false
        deviceState["wifi"] = true
        deviceState["bluetooth"] = false
        deviceState["cellular"] = true
        deviceState["media_playing"] = false
        deviceState["media_track"] = "No song playing"
        deviceState["meetings"] = CopyOnWriteArrayList<String>()
        deviceState["reminders"] = CopyOnWriteArrayList<String>()
        deviceState["rotation"] = true
    }

    fun getRotation(): Boolean = deviceState["rotation"] as? Boolean ?: true
    fun setRotation(state: Boolean) {
        deviceState["rotation"] = state
    }

    fun getVolume(): Int = deviceState["volume"] as? Int ?: 50
    fun setVolume(level: Int) {
        deviceState["volume"] = level.coerceIn(0, 100)
    }

    fun getBrightness(): Int = deviceState["brightness"] as? Int ?: 70
    fun setBrightness(level: Int) {
        deviceState["brightness"] = level.coerceIn(10, 100)
    }

    fun getFlashlight(): Boolean = deviceState["flashlight"] as? Boolean ?: false
    fun setFlashlight(state: Boolean) {
        deviceState["flashlight"] = state
    }

    fun getWifi(): Boolean = deviceState["wifi"] as? Boolean ?: true
    fun getBluetooth(): Boolean = deviceState["bluetooth"] as? Boolean ?: false
    fun getCellular(): Boolean = deviceState["cellular"] as? Boolean ?: true
    
    fun setNetworks(wifi: Boolean, bluetooth: Boolean, cellular: Boolean) {
        deviceState["wifi"] = wifi
        deviceState["bluetooth"] = bluetooth
        deviceState["cellular"] = cellular
    }

    fun getMediaPlaying(): Boolean = deviceState["media_playing"] as? Boolean ?: false
    fun getMediaTrack(): String = deviceState["media_track"] as? String ?: "No song playing"
    
    fun setMediaState(track: String, playing: Boolean) {
        deviceState["media_track"] = track
        deviceState["media_playing"] = playing
    }

    @Suppress("UNCHECKED_CAST")
    fun getMeetings(): List<String> = deviceState["meetings"] as? List<String> ?: emptyList()
    
    fun addMeeting(meeting: String) {
        val list = deviceState["meetings"] as? CopyOnWriteArrayList<String> ?: CopyOnWriteArrayList()
        list.add(meeting)
        deviceState["meetings"] = list
    }

    @Suppress("UNCHECKED_CAST")
    fun getReminders(): List<String> = deviceState["reminders"] as? List<String> ?: emptyList()
    
    fun addReminder(reminder: String) {
        val list = deviceState["reminders"] as? CopyOnWriteArrayList<String> ?: CopyOnWriteArrayList()
        list.add(reminder)
        deviceState["reminders"] = list
    }

    /**
     * Resets the execution log traces for a new user request turn.
     */
    fun clearTrace() {
        executionTrace.clear()
    }

    /**
     * Logs a system action or agent transition.
     */
    fun addTrace(log: String) {
        executionTrace.add(log)
    }
}

data class Message(
    val role: String, // "user", "assistant", or "system"
    val content: String
)
