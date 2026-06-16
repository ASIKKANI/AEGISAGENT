package com.edgeai.aegisagent.agents

import com.edgeai.aegisagent.core.*
import kotlinx.serialization.json.*

/**
 * Base Agent abstraction representing a cognitive module with custom system instructions,
 * a local tool registry, and an execution loop.
 */
abstract class Agent(
    val name: String,
    val systemPrompt: String,
    val tools: List<Tool> = emptyList()
) {
    /**
     * Compile the available tools into FunctionGemma-compliant tags
     * to populate the agent's system prompt instructions.
     */
    protected fun compileSystemPrompt(context: AgentContext): String {
        // If running in local fine-tuned Ollama mode, we bypass compiling the tool declarations
        // to match the clean SFT training dataset prompts exactly.
        if (!context.isSimulationMode) return systemPrompt

        if (tools.isEmpty()) return systemPrompt

        val sb = StringBuilder(systemPrompt)
        sb.append("\n\nYou have access to the following tools:\n")
        for (tool in tools) {
            sb.append("<start_function_declaration>declaration:${tool.name}{")
            sb.append("description:<escape>${tool.description}<escape>,")
            sb.append("parameters:{")
            val paramsStr = tool.parameters.entries.joinToString(", ") { (k, v) ->
                "$k:<escape>$v<escape>"
            }
            sb.append(paramsStr)
            sb.append("}}<end_function_declaration>\n")
        }
        sb.append("\nWhen you need to call a tool, respond ONLY with standard control tags in format: <start_function_call>tool_name{key: value}<end_function_call> without markdown formatting. Do not output conversational text once a tool is triggered.")
        return sb.toString()
    }

    /**
     * Executes the agent loop:
     * 1. Query the edge model runner.
     * 2. Parse potential tool call structures.
     * 3. Invoke matched tool and apply context modifications.
     * 4. Auto-repair malformed parameters.
     */
    open suspend fun execute(
        userPrompt: String,
        context: AgentContext,
        modelRunner: EdgeModelRunner
    ): String {
        context.addTrace("Agent [$name] starting reasoning turn...")
        val activeSystemPrompt = compileSystemPrompt(context)
        
        val rawResponse = modelRunner.generate(
            systemPrompt = activeSystemPrompt,
            userPrompt = userPrompt,
            stopTokens = listOf("<end_function_call>")
        )

        context.addTrace("Agent [$name] raw model output: $rawResponse")

        // Extract function call block with robust prefix and fallback parsing
        val toolsToExecute = mutableListOf<Pair<String, String>>()

        if (rawResponse.contains("<start_function_call>")) {
            var tempResponse = rawResponse
            while (tempResponse.contains("<start_function_call>")) {
                val callBlock = tempResponse.substringAfter("<start_function_call>").substringBefore("<end_function_call>").trim()
                val rawName = callBlock.substringBefore("{").trim()
                val args = "{" + callBlock.substringAfter("{").substringBeforeLast("}") + "}"
                toolsToExecute.add(Pair(rawName, args))
                tempResponse = tempResponse.substringAfter("<end_function_call>")
            }
        } else {
            // Fallback: search if any registered tool name (with or without domain prefix) exists in response
            for (tool in tools) {
                val prefixes = listOf("", "device.", "media.", "camera.", "calendar.")
                for (prefix in prefixes) {
                    val pattern = "$prefix${tool.name}{"
                    if (rawResponse.contains(pattern)) {
                        val afterPattern = rawResponse.substringAfter(pattern)
                        val args = "{" + afterPattern.substringBefore("}").trim() + "}"
                        toolsToExecute.add(Pair(tool.name, args))
                        break
                    }
                }
            }
        }
        
        // Heuristic fallback parsing on userPrompt when the model failed to output a standard tag format
        if (toolsToExecute.isEmpty()) {
            val userLower = userPrompt.lowercase()
            
            // 1. Flashlight
            if (userLower.contains("flashlight") || userLower.contains("torch") || userLower.contains("flash")) {
                val state = !userLower.contains("off") && !userLower.contains("disable") && !userLower.contains("stop")
                toolsToExecute.add(Pair("device.set_flashlight", "{\"state\": $state}"))
            }
            // 2. Volume
            if (userLower.contains("volume") || userLower.contains("mute") || userLower.contains("silent")) {
                var num = "\\d+".toRegex().find(userLower)?.value?.toIntOrNull() ?: 50
                if (userLower.contains("max") || userLower.contains("maximum") || userLower.contains("full") || userLower.contains("high")) {
                    num = 100
                } else if (userLower.contains("mute") || userLower.contains("silent") || userLower.contains("zero") || userLower.contains("off") || userLower.contains("min")) {
                    num = 0
                } else if (userLower.contains("half") || userLower.contains("medium") || userLower.contains("mid")) {
                    num = 50
                } else if (num in 1..10) {
                    num *= 10
                }
                toolsToExecute.add(Pair("device.set_volume", "{\"level\": $num}"))
            }
            // 3. Brightness
            if (userLower.contains("brightness") || userLower.contains("dim") || userLower.contains("screen")) {
                if (!userLower.contains("volume")) {
                    var num = "\\d+".toRegex().find(userLower)?.value?.toIntOrNull() ?: 70
                    if (userLower.contains("max") || userLower.contains("maximum") || userLower.contains("full") || userLower.contains("high")) {
                        num = 100
                    } else if (userLower.contains("min") || userLower.contains("dim") || userLower.contains("low")) {
                        num = 15
                    } else if (userLower.contains("half") || userLower.contains("medium") || userLower.contains("mid")) {
                        num = 50
                    } else if (num in 1..10) {
                        num *= 10
                    }
                    toolsToExecute.add(Pair("device.set_brightness", "{\"level\": $num}"))
                }
            }
            // 4. Configure networks
            if (userLower.contains("wifi") || userLower.contains("wi-fi") || userLower.contains("bluetooth") || userLower.contains("network")) {
                val wifi = !userLower.contains("off wifi") && !userLower.contains("disable wifi") && !userLower.contains("turn off wi-fi")
                val bt = userLower.contains("enable bluetooth") || userLower.contains("turn on bluetooth")
                toolsToExecute.add(Pair("device.configure_networks", "{\"wifi\": $wifi, \"bluetooth\": $bt, \"cellular\": true}"))
            }
            // 5. Rotation
            if (userLower.contains("rotation") || userLower.contains("rotate")) {
                val state = !userLower.contains("lock") && !userLower.contains("disable")
                toolsToExecute.add(Pair("device.set_rotation", "{\"state\": $state}"))
            }
            // 6. Media Play Music
            if (userLower.contains("play")) {
                val track = userLower.substringAfter("play ").substringBefore(" music").substringBefore(" song").trim()
                if (track.isNotEmpty() && track != "some" && track != "music" && track != "song") {
                    toolsToExecute.add(Pair("media.play_music", "{\"track\": \"$track\", \"playing\": true}"))
                } else {
                    toolsToExecute.add(Pair("media.control_playback", "{\"playing\": true}"))
                }
            }
            // 7. Media Control Playback
            else if (userLower.contains("pause") || userLower.contains("stop") || userLower.contains("resume")) {
                val playing = userLower.contains("resume")
                toolsToExecute.add(Pair("media.control_playback", "{\"playing\": $playing}"))
            }
            // 8. Camera Take Photo
            if (userLower.contains("photo") || userLower.contains("camera") || userLower.contains("snap") || userLower.contains("capture") || userLower.contains("picture")) {
                if (!userLower.contains("show") && !userLower.contains("view") && !userLower.contains("display") && !userLower.contains("open last")) {
                    val filter = when {
                        userLower.contains("vintage") -> "vintage"
                        userLower.contains("noir") || userLower.contains("black and white") -> "noir"
                        userLower.contains("neon") -> "neon"
                        else -> "default"
                    }
                    toolsToExecute.add(Pair("camera.take_photo", "{\"filter\": \"$filter\"}"))
                }
            }
            // 9. Calendar Add Meeting
            if (userLower.contains("meeting") || userLower.contains("schedule") || userLower.contains("calendar")) {
                if (!userLower.contains("timer") && !userLower.contains("reminder") && !userLower.contains("remind") && !userLower.contains("alarm")) {
                    val title = userLower.substringAfter("schedule a ", "").substringAfter("meeting called ", "").substringBefore(" tomorrow", "").substringBefore(" on", "").trim().ifEmpty { "sync" }
                    val date = if (userLower.contains("tomorrow")) "tomorrow" else "today"
                    val time = "\\d+ (?:am|pm|PM|AM)".toRegex().find(userLower)?.value ?: "3 PM"
                    toolsToExecute.add(Pair("calendar.add_meeting", "{\"title\": \"$title\", \"date\": \"$date\", \"time\": \"$time\"}"))
                }
            }
            // 10. Calendar Create Reminder
            if (userLower.contains("reminder") || userLower.contains("timer") || userLower.contains("remind") || userLower.contains("alarm")) {
                val title = if (userLower.contains("remind me to ")) {
                    userLower.substringAfter("remind me to ").substringBefore(" in ").trim()
                } else if (userLower.contains("timer for ")) {
                    "timer"
                } else {
                    "reminder"
                }
                val delay = "\\d+".toRegex().find(userLower)?.value?.toIntOrNull() ?: 15
                toolsToExecute.add(Pair("calendar.create_reminder", "{\"title\": \"$title\", \"delay_minutes\": $delay}"))
            }
        }

        if (toolsToExecute.isNotEmpty()) {
            val results = mutableListOf<String>()
            for ((toolName, argsRaw) in toolsToExecute) {
                context.addTrace("Agent [$name] parsed tool invocation request: '$toolName' with parameters '$argsRaw'")

                val matchedTool = tools.find { 
                    it.name.equals(toolName, ignoreCase = true) || 
                    (it.name.contains(".") && it.name.substringAfter(".").equals(toolName, ignoreCase = true)) ||
                    (toolName.contains(".") && toolName.substringAfter(".").equals(it.name, ignoreCase = true))
                }
                if (matchedTool != null) {
                    val parsedArgs = parseArguments(argsRaw).toMutableMap()
                    
                    // Central normalization for volume and brightness levels
                    if (matchedTool.name.contains("volume", ignoreCase = true) || matchedTool.name.contains("brightness", ignoreCase = true)) {
                        var level = (parsedArgs["level"] as? Number)?.toInt()
                        if (level != null) {
                            val userLower = userPrompt.lowercase()
                            if (userLower.contains("max") || userLower.contains("maximum") || userLower.contains("full") || userLower.contains("high")) {
                                level = 100
                            } else if (userLower.contains("mute") || userLower.contains("silent") || userLower.contains("zero") || userLower.contains("off") || userLower.contains("min")) {
                                level = if (matchedTool.name.contains("brightness", ignoreCase = true)) 15 else 0
                            } else if (userLower.contains("half") || userLower.contains("medium") || userLower.contains("mid")) {
                                level = 50
                            } else if (level in 1..10) {
                                level *= 10
                            }
                            parsedArgs["level"] = level
                        }
                    }

                    context.addTrace("Agent [$name] executing tool '${matchedTool.name}'...")
                    val result = matchedTool.execute(parsedArgs, context)
                    context.addTrace("Agent [$name] tool execution result: $result")
                    results.add(result)
                } else {
                    context.addTrace("Agent [$name] error: Target tool '$toolName' is not registered.")
                    results.add("Error: Tool '$toolName' is not available.")
                }
            }
            return results.joinToString("\n")
        }

        // Default conversational response if no tool was invoked
        return rawResponse
    }

    /**
     * A robust parameter extractor.
     * Attempts to parse standard JSON, with simple key-value split cleanups
     * if the model outputs malformed strings.
     */
    private fun parseArguments(argsRaw: String): Map<String, Any> {
        val arguments = mutableMapOf<String, Any>()
        try {
            val jsonElement = Json.parseToJsonElement(argsRaw).jsonObject
            for ((key, value) in jsonElement) {
                val primitive = value.jsonPrimitive
                when {
                    primitive.isString -> arguments[key] = primitive.content
                    primitive.booleanOrNull != null -> arguments[key] = primitive.boolean
                    primitive.intOrNull != null -> arguments[key] = primitive.int
                    else -> arguments[key] = primitive.content
                }
            }
        } catch (e: Exception) {
            // Heuristic fallback parser for broken JSON string (e.g. {state: true} or {track: "Lo-Fi"})
            val cleaned = argsRaw.trim('{', '}')
            val pairs = cleaned.split(",")
            for (pair in pairs) {
                if (pair.contains(":")) {
                    val key = pair.substringBefore(":").trim().trim('"', '\'')
                    val rawVal = pair.substringAfter(":").trim().trim('"', '\'')
                    when {
                        rawVal.equals("true", ignoreCase = true) -> arguments[key] = true
                        rawVal.equals("false", ignoreCase = true) -> arguments[key] = false
                        rawVal.toIntOrNull() != null -> arguments[key] = rawVal.toInt()
                        else -> arguments[key] = rawVal
                    }
                }
            }
        }
        return arguments
    }
}

/**
 * The Central Router Agent.
 * Evaluates the user query to classify the domain target tag.
 */
class IntentRouterAgent : Agent(
    name = "IntentRouter",
    systemPrompt = "You are the central intent router for AegisAgent. Classify the user instruction to one of: [route: system], [route: media], [route: camera], or [route: productivity]."
) {
    suspend fun route(userPrompt: String, modelRunner: EdgeModelRunner): String {
        val query = userPrompt.trim().lowercase()
        
        // High-precision keyword routing layer
        val matchedRoute = when {
            // Productivity domain triggers
            query.contains("timer") || query.contains("alarm") || query.contains("remind") || 
            query.contains("reminder") || query.contains("schedule") || query.contains("meeting") || 
            query.contains("calendar") || query.contains("todo") || query.contains("task") ||
            query.contains("appointment") || query.contains("event") -> "productivity"
            
            // Camera domain triggers
            query.contains("photo") || query.contains("camera") || query.contains("snap") || 
            query.contains("picture") || query.contains("filter") || query.contains("lens") || 
            query.contains("capture") || query.contains("viewfinder") -> "camera"
            
            // Media domain triggers
            query.contains("play") || query.contains("pause") || query.contains("music") || 
            query.contains("song") || query.contains("track") || query.contains("resume") || 
            query.contains("audio") || query.contains("video") || query.contains("playback") -> "media"
            
            // System Control domain triggers
            query.contains("flashlight") || query.contains("torch") || query.contains("flash") || 
            query.contains("brightness") || query.contains("volume") || query.contains("mute") || 
            query.contains("wifi") || query.contains("wi-fi") || query.contains("bluetooth") || 
            query.contains("rotation") || query.contains("rotate") || query.contains("cellular") || 
            query.contains("network") -> "system"
            
            else -> null
        }
        
        if (matchedRoute != null) {
            return matchedRoute
        }

        // Fallback to LLM intent classification if no static keywords match
        val result = modelRunner.generate(systemPrompt, userPrompt).lowercase()
        return when {
            result.contains("system") -> "system"
            result.contains("media") -> "media"
            result.contains("camera") -> "camera"
            result.contains("productivity") || result.contains("calendar") || result.contains("scheduler") -> "productivity"
            else -> "system" // Fallback target
        }
    }
}

/**
 * Executes system configuration controls (volume, network, flash).
 */
class SystemControlAgent(systemPrompt: String, tools: List<Tool>) : Agent(
    name = "SystemControl",
    systemPrompt = systemPrompt,
    tools = tools
)

class MediaControlAgent(systemPrompt: String, tools: List<Tool>) : Agent(
    name = "MediaControl",
    systemPrompt = systemPrompt,
    tools = tools
)

class CameraAgent(systemPrompt: String, tools: List<Tool>) : Agent(
    name = "CameraExecutor",
    systemPrompt = systemPrompt,
    tools = tools
)

class ProductivityAgent(systemPrompt: String, tools: List<Tool>) : Agent(
    name = "ProductivityScheduler",
    systemPrompt = systemPrompt,
    tools = tools
)
