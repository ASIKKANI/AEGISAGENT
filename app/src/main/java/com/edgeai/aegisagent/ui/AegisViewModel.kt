package com.edgeai.aegisagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edgeai.aegisagent.core.*
import com.edgeai.aegisagent.dsl.*
import com.edgeai.aegisagent.system.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel acting as the state bridge between KOrchestra framework and Jetpack Compose screens.
 * Wire agent tools to physical Android system APIs.
 */
class AegisViewModel(application: Application) : AndroidViewModel(application) {

    private val systemController = SystemController(application)
    private val cameraController = CameraController(application)
    private val notificationController = NotificationController(application)

    private val ollamaRunner = OllamaModelRunner("http://127.0.0.1:11434", "minicpm-aegis:latest")
    private val simulatedRunner = SimulatedModelRunner()

    private val _isSimulationMode = MutableStateFlow(false) // Default to Ollama mode!
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    // Dynamically delegate to the active model runner
    private val modelRunner = object : EdgeModelRunner {
        override suspend fun generate(systemPrompt: String, userPrompt: String, stopTokens: List<String>): String {
            return if (_isSimulationMode.value) {
                simulatedRunner.generate(systemPrompt, userPrompt, stopTokens)
            } else {
                ollamaRunner.generate(systemPrompt, userPrompt, stopTokens)
            }
        }
    }

    private val orchestrator: AgentOrchestrator

    // UI state flows
    private val _isWarmingUp = MutableStateFlow(true)
    val isWarmingUp: StateFlow<Boolean> = _isWarmingUp.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<Message>>(emptyList())
    val chatHistory: StateFlow<List<Message>> = _chatHistory.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs: StateFlow<List<String>> = _consoleLogs.asStateFlow()

    // Screen display configurations mapped to context
    private val _volumeLevel = MutableStateFlow(50)
    val volumeLevel: StateFlow<Int> = _volumeLevel.asStateFlow()

    private val _brightnessLevel = MutableStateFlow(70)
    val brightnessLevel: StateFlow<Int> = _brightnessLevel.asStateFlow()

    private val _flashlightState = MutableStateFlow(false)
    val flashlightState: StateFlow<Boolean> = _flashlightState.asStateFlow()

    private val _wifiState = MutableStateFlow(true)
    val wifiState: StateFlow<Boolean> = _wifiState.asStateFlow()

    private val _bluetoothState = MutableStateFlow(false)
    val bluetoothState: StateFlow<Boolean> = _bluetoothState.asStateFlow()

    private val _rotationState = MutableStateFlow(true)
    val rotationState: StateFlow<Boolean> = _rotationState.asStateFlow()

    private val _mediaPlaying = MutableStateFlow(false)
    val mediaPlaying: StateFlow<Boolean> = _mediaPlaying.asStateFlow()

    private val _mediaTrack = MutableStateFlow("No song playing")
    val mediaTrack: StateFlow<String> = _mediaTrack.asStateFlow()

    private val _meetingsList = MutableStateFlow<List<String>>(emptyList())
    val meetingsList: StateFlow<List<String>> = _meetingsList.asStateFlow()

    init {
        // Build the orchestrator using our type-safe Kotlin DSL
        orchestrator = agentOrchestrator {
            modelRunner = this@AegisViewModel.modelRunner

            systemAgent {
                systemPrompt = "You are the System Agent. Execute system hardware commands using set_flashlight, set_volume, set_brightness, configure_networks, or set_rotation."
                tool("device.set_flashlight", "Toggle device torch.") {
                    parameter("state", "boolean")
                    onExecute { args, context ->
                        val state = args["state"] as? Boolean ?: false
                        context.setFlashlight(state)
                        _flashlightState.value = state
                        cameraController.setFlashlight(state)
                    }
                }
                tool("device.set_volume", "Adjust media volume.") {
                    parameter("level", "integer")
                    onExecute { args, context ->
                        val level = (args["level"] as? Number)?.toInt() ?: 50
                        context.setVolume(level)
                        _volumeLevel.value = level
                        systemController.setVolume(level)
                    }
                }
                tool("device.set_brightness", "Adjust screen brightness.") {
                    parameter("level", "integer")
                    onExecute { args, context ->
                        val level = (args["level"] as? Number)?.toInt() ?: 70
                        context.setBrightness(level)
                        _brightnessLevel.value = level
                        systemController.setBrightness(level)
                    }
                }
                tool("device.configure_networks", "Configure Wi-Fi, Bluetooth, and cellular network connections.") {
                    parameter("wifi", "boolean")
                    parameter("bluetooth", "boolean")
                    parameter("cellular", "boolean")
                    onExecute { args, context ->
                        val wifi = args["wifi"] as? Boolean ?: true
                        val bt = args["bluetooth"] as? Boolean ?: false
                        val cellular = args["cellular"] as? Boolean ?: true
                        context.setNetworks(wifi, bt, cellular)
                        _wifiState.value = wifi
                        _bluetoothState.value = bt
                        systemController.configureNetworks(wifi, bt, cellular)
                    }
                }
                tool("device.set_rotation", "Toggle screen auto-rotation.") {
                    parameter("state", "boolean")
                    onExecute { args, context ->
                        val state = args["state"] as? Boolean ?: true
                        context.setRotation(state)
                        _rotationState.value = state
                        "Screen auto-rotation set to $state."
                    }
                }
            }

            mediaAgent {
                systemPrompt = "You are the Media Agent. Control audio and playback using play_music or control_playback."
                tool("media.play_music", "Play specified track.") {
                    parameter("track", "string")
                    parameter("playing", "boolean")
                    onExecute { args, context ->
                        val track = args["track"]?.toString() ?: "Unknown track"
                        val playing = args["playing"] as? Boolean ?: true
                        context.setMediaState(track, playing)
                        _mediaTrack.value = track
                        _mediaPlaying.value = playing
                        "Successfully playing track '$track'."
                    }
                }
                tool("media.control_playback", "Toggle play/pause status.") {
                    parameter("playing", "boolean")
                    onExecute { args, context ->
                        val playing = args["playing"] as? Boolean ?: false
                        context.setMediaState(context.getMediaTrack(), playing)
                        _mediaPlaying.value = playing
                        "Media playback status toggled: Playing = $playing."
                    }
                }
            }

            cameraAgent {
                systemPrompt = "You are the Camera Agent. Open camera viewfinder and snap pictures using take_photo."
                tool("camera.take_photo", "Capture photo snapshot.") {
                    parameter("filter", "string")
                    onExecute { args, context ->
                        val filter = args["filter"]?.toString() ?: "default"
                        cameraController.takePhoto(filter)
                    }
                }
            }

            productivityAgent {
                systemPrompt = "You are the Productivity Agent. Manage calendar events and reminders using add_meeting or create_reminder."
                tool("calendar.add_meeting", "Create calendar event.") {
                    parameter("title", "string")
                    parameter("date", "string")
                    parameter("time", "string")
                    onExecute { args, context ->
                        val title = args["title"]?.toString() ?: "Scrum Sync"
                        val date = args["date"]?.toString() ?: "today"
                        val time = args["time"]?.toString() ?: "3 PM"
                        context.addMeeting("$title ($date @ $time)")
                        _meetingsList.value = context.getMeetings()
                        notificationController.scheduleCalendarMeeting(title, date, time)
                    }
                }
                tool("calendar.create_reminder", "Set local alerts.") {
                    parameter("title", "string")
                    parameter("delay_minutes", "integer")
                    onExecute { args, context ->
                        val title = args["title"]?.toString() ?: "Reminder"
                        val delay = (args["delay_minutes"] as? Number)?.toInt() ?: 15
                        context.addReminder("$title in $delay min")
                        notificationController.scheduleReminder(title, delay)
                    }
                }
            }
        }

        // Initialize simulation mode flag in orchestrator context
        orchestrator.context.isSimulationMode = _isSimulationMode.value

        // Asynchronous background pre-warming of model parameters
        viewModelScope.launch {
            delay(1500) // Simulate cold-start pre-warming delay (or ping local server)
            _isWarmingUp.value = false
            _consoleLogs.value = listOf(
                "==================================",
                " AEGIS COGNITIVE OS INITIALIZED",
                " System Warm-up Status: READY",
                " Edge Model: minicpm-aegis:latest (2.7 GB)",
                " Base Endpoint: http://127.0.0.1:11434",
                "=================================="
            )
        }
    }

    /**
     * Entry hook from UI user text submission.
     * Executes asynchronous reasoning tasks off the main thread.
     */
    fun submitQuery(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _consoleLogs.value = _consoleLogs.value + "> User: $query"
            _chatHistory.value = _chatHistory.value + Message("user", query)

            try {
                // Execute routing and tool calls off-main-thread
                val result = orchestrator.processQuery(query)
                
                // Update lists
                _chatHistory.value = orchestrator.context.history.toList()
                
                // Color and append trace logs
                val formattedLogs = orchestrator.context.executionTrace.map { log ->
                    when {
                        log.contains("starting", true) -> "[SYSTEM] $log"
                        log.contains("parsed", true) -> "[PARSER] $log"
                        log.contains("executing", true) -> "[EXEC] $log"
                        log.contains("result", true) -> "[RESULT] $log"
                        else -> log
                    }
                }
                _consoleLogs.value = _consoleLogs.value + formattedLogs + "< Assistant: $result"
            } catch (e: Exception) {
                val err = "Error coordinating agents: ${e.message}"
                _consoleLogs.value = _consoleLogs.value + "[ERROR] $err"
                _chatHistory.value = _chatHistory.value + Message("assistant", "Sorry, an internal error occurred.")
            }
        }
    }

    // Manual control actions wired directly from UI sliders and buttons
    fun setVolume(level: Int) {
        _volumeLevel.value = level
        systemController.setVolume(level)
    }

    fun setBrightness(level: Int) {
        _brightnessLevel.value = level
        systemController.setBrightness(level)
    }

    fun toggleWifi() {
        val newState = !_wifiState.value
        _wifiState.value = newState
        systemController.configureNetworks(newState, _bluetoothState.value, true)
    }

    fun toggleBluetooth() {
        val newState = !_bluetoothState.value
        _bluetoothState.value = newState
        systemController.configureNetworks(_wifiState.value, newState, true)
    }

    fun toggleFlashlight() {
        val newState = !_flashlightState.value
        _flashlightState.value = newState
        cameraController.setFlashlight(newState)
    }

    fun toggleRotation() {
        val newState = !_rotationState.value
        _rotationState.value = newState
    }

    fun toggleSimulationMode() {
        _isSimulationMode.value = !_isSimulationMode.value
        orchestrator.context.isSimulationMode = _isSimulationMode.value
        val modeName = if (_isSimulationMode.value) "OFFLINE SIMULATION" else "LOCAL OLLAMA NODE"
        _consoleLogs.value = _consoleLogs.value + "[SYSTEM] Switched model execution to $modeName."
    }
}
