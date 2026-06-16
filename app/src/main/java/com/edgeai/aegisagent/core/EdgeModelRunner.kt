package com.edgeai.aegisagent.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Common abstraction for local edge AI LLM inference.
 */
interface EdgeModelRunner {
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        stopTokens: List<String> = emptyList()
    ): String
}

/**
 * Connects to a local Ollama server running on the host machine.
 * Note: On Android Emulator, the host machine is accessed via IP '10.0.2.2'.
 */
class OllamaModelRunner(
    private val baseUrl: String = "http://10.0.2.2:11434",
    private val modelName: String = "MiniCPM5-1B-Q4_K_M:latest"
) : EdgeModelRunner {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        stopTokens: List<String>
    ): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/chat"

        // Build the Chat request JSON structure expected by Ollama
        val requestJson = buildJsonObject {
            put("model", modelName)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                }
            }
            put("stream", false)
            putJsonObject("options") {
                put("temperature", 0.0) // 0.0 for deterministic tool outputs
                putJsonArray("stop") {
                    stopTokens.forEach { add(it) }
                }
            }
        }

        val body = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Ollama returned unsuccessful code: ${response.code}")
                }
                val bodyString = response.body?.string() ?: throw IOException("Empty response body from Ollama")
                
                // Parse the response
                val responseJson = json.parseToJsonElement(bodyString).jsonObject
                val message = responseJson["message"]?.jsonObject
                val content = message?.get("content")?.jsonPrimitive?.content ?: ""
                content.trim()
            }
        } catch (e: Exception) {
            // Log error and propagate up
            "ERROR: Failed to connect to local model server. Ensure Ollama is running. (Details: ${e.message})"
        }
    }
}

/**
 * A fast, offline rule-based model simulator.
 * Provides instant responses following our fine-tuned schema without requiring an active server connection.
 */
class SimulatedModelRunner : EdgeModelRunner {
    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        stopTokens: List<String>
    ): String = withContext(Dispatchers.Default) {
        val query = userPrompt.trim().lowercase()

        // 1. Check if we are running the Router Agent
        if (systemPrompt.contains("router", ignoreCase = true)) {
            return@withContext when {
                query.contains("flashlight") || query.contains("wifi") || query.contains("bluetooth") || 
                query.contains("volume") || query.contains("brightness") || query.contains("mute") || query.contains("cellular") -> "[route: system]"
                
                query.contains("play") || query.contains("pause") || query.contains("track") || 
                query.contains("music") || query.contains("song") || query.contains("skip") -> "[route: media]"
                
                query.contains("photo") || query.contains("camera") || query.contains("snap") || 
                query.contains("viewfinder") || query.contains("capture") || query.contains("shot") -> "[route: camera]"
                
                query.contains("schedule") || query.contains("meeting") || query.contains("remind") || 
                query.contains("alarm") || query.contains("timer") || query.contains("task") || query.contains("todo") -> "[route: productivity]"
                
                else -> "[route: system]" // Default fallback
            }
        }

        // 2. Check if we are running the System Agent
        if (systemPrompt.contains("System Control", ignoreCase = true) || systemPrompt.contains("System Agent", ignoreCase = true)) {
            return@withContext when {
                query.contains("flashlight") -> {
                    val state = !query.contains("off") && !query.contains("disable")
                    "<start_function_call>device.set_flashlight{state: $state}<end_function_call>"
                }
                query.contains("volume") -> {
                    val number = "\\d+".toRegex().find(query)?.value?.toInt() ?: 50
                    "<start_function_call>device.set_volume{level: $number}<end_function_call>"
                }
                query.contains("mute") -> "<start_function_call>device.set_volume{level: 0}<end_function_call>"
                query.contains("brightness") -> {
                    val number = "\\d+".toRegex().find(query)?.value?.toInt() ?: 70
                    "<start_function_call>device.set_brightness{level: $number}<end_function_call>"
                }
                query.contains("wi-fi") || query.contains("wifi") || query.contains("bluetooth") || query.contains("network") -> {
                    val wifi = !query.contains("off wi-fi") && !query.contains("disable wifi")
                    val bluetooth = query.contains("enable bluetooth") || query.contains("turn on bluetooth")
                    "<start_function_call>device.configure_networks{wifi: $wifi, bluetooth: $bluetooth, cellular: true}<end_function_call>"
                }
                query.contains("rotation") || query.contains("rotate") -> {
                    val state = !query.contains("lock") && !query.contains("disable")
                    "<start_function_call>device.set_rotation{state: $state}<end_function_call>"
                }
                else -> "I don't know how to map this system command."
            }
        }

        // 3. Check if we are running the Media Agent
        if (systemPrompt.contains("Media Control", ignoreCase = true) || systemPrompt.contains("Media Agent", ignoreCase = true)) {
            return@withContext when {
                query.contains("play") -> {
                    val track = query.substringAfter("play ").substringBefore(" music").substringBefore(" song").trim()
                    "<start_function_call>media.play_music{track: \"$track\", playing: true}<end_function_call>"
                }
                query.contains("pause") -> "<start_function_call>media.control_playback{playing: false}<end_function_call>"
                query.contains("resume") -> "<start_function_call>media.control_playback{playing: true}<end_function_call>"
                query.contains("stop") -> "<start_function_call>media.control_playback{playing: false}<end_function_call>"
                else -> "<start_function_call>media.control_playback{playing: true}<end_function_call>"
            }
        }

        // 4. Check if we are running the Camera Agent
        if (systemPrompt.contains("Camera Agent", ignoreCase = true)) {
            return@withContext when {
                query.contains("vintage") -> "<start_function_call>camera.take_photo{filter: \"vintage\"}<end_function_call>"
                query.contains("noir") || query.contains("black and white") -> "<start_function_call>camera.take_photo{filter: \"noir\"}<end_function_call>"
                query.contains("neon") -> "<start_function_call>camera.take_photo{filter: \"neon\"}<end_function_call>"
                else -> "<start_function_call>camera.take_photo{filter: \"default\"}<end_function_call>"
            }
        }

        // 5. Check if we are running the Productivity Agent
        if (systemPrompt.contains("Productivity Agent", ignoreCase = true)) {
            return@withContext when {
                query.contains("schedule") || query.contains("meeting") || query.contains("add") -> {
                    val title = query.substringAfter("schedule a ").substringAfter("meeting called ").substringBefore(" tomorrow").substringBefore(" on").substringBefore(" tomorrow").trim()
                    val time = "\\d+ (?:am|pm|PM|AM)".toRegex().find(query)?.value ?: "3 PM"
                    "<start_function_call>calendar.add_meeting{title: \"$title\", date: \"tomorrow\", time: \"$time\"}<end_function_call>"
                }
                query.contains("remind") || query.contains("timer") -> {
                    val minutes = "\\d+".toRegex().find(query)?.value?.toInt() ?: 15
                    val title = query.substringAfter("remind me to ").substringBefore(" in ").trim()
                    "<start_function_call>calendar.create_reminder{title: \"$title\", delay_minutes: $minutes}<end_function_call>"
                }
                else -> "I don't know how to schedule this."
            }
        }

        "ERROR: Unknown Agent system prompt"
    }
}
