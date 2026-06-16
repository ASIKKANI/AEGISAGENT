package com.edgeai.aegisagent

import com.edgeai.aegisagent.core.AgentContext
import com.edgeai.aegisagent.core.SimulatedModelRunner
import com.edgeai.aegisagent.dsl.agentOrchestrator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying that AegisAgent core routing, tool coordination,
 * and context state modifications behave correctly under JVM memory space.
 */
class OrchestratorTest {

    @Test
    fun testOrchestratorDSLCompilation() {
        val orchestrator = agentOrchestrator {
            modelRunner = SimulatedModelRunner()
            
            systemAgent {
                systemPrompt = "System settings"
                tool("device.set_volume", "Set volume") {
                    parameter("level", "integer")
                    onExecute { args, context ->
                        val level = args["level"] as Int
                        context.setVolume(level)
                        "Volume set"
                    }
                }
            }
        }

        assertNotNull("DSL Orchestrator should compile to non-null value", orchestrator)
        assertNotNull("Orchestrator context should be instantiated", orchestrator.context)
        assertEquals("Should register 'system' agent", true, orchestrator.agents.containsKey("system"))
    }

    @Test
    fun testContextStateMutations() {
        val context = AgentContext()

        // Assert defaults
        assertEquals(50, context.getVolume())
        assertEquals(70, context.getBrightness())
        assertFalse(context.getFlashlight())
        assertTrue(context.getWifi())
        assertFalse(context.getBluetooth())

        // Mutate and assert
        context.setVolume(85)
        assertEquals(85, context.getVolume())

        context.setBrightness(120) // Test constraint boundaries (coerceIn)
        assertEquals(100, context.getBrightness())

        context.setFlashlight(true)
        assertTrue(context.getFlashlight())

        context.setNetworks(wifi = false, bluetooth = true, cellular = false)
        assertFalse(context.getWifi())
        assertTrue(context.getBluetooth())
        assertFalse(context.getCellular())
    }

    @Test
    fun testRouterAndSystemExecutionFlow() = runBlocking {
        // Build simulated system orchestrator
        val orchestrator = agentOrchestrator {
            modelRunner = SimulatedModelRunner()

            systemAgent {
                systemPrompt = "System agent"
                tool("device.set_flashlight", "Toggle flashlight") {
                    parameter("state", "boolean")
                    onExecute { args, context ->
                        val state = args["state"] as Boolean
                        context.setFlashlight(state)
                        "Flashlight toggled to $state"
                    }
                }
            }
        }

        // Execute system command
        val result = orchestrator.processQuery("Turn on the flashlight")

        assertEquals("Flashlight toggled to true", result)
        assertTrue(orchestrator.context.getFlashlight())
        assertTrue(orchestrator.context.executionTrace.any { it.contains("device.set_flashlight") })
    }

    @Test
    fun testRouterAndMediaExecutionFlow() = runBlocking {
        val orchestrator = agentOrchestrator {
            modelRunner = SimulatedModelRunner()

            mediaAgent {
                systemPrompt = "Media agent"
                tool("media.play_music", "Play music") {
                    parameter("track", "string")
                    parameter("playing", "boolean")
                    onExecute { args, context ->
                        val track = args["track"] as String
                        val playing = args["playing"] as Boolean
                        context.setMediaState(track, playing)
                        "Playing track $track"
                    }
                }
            }
        }

        val result = orchestrator.processQuery("Play chill beats music")

        assertEquals("Playing track chill beats", result)
        assertTrue(orchestrator.context.getMediaPlaying())
        assertEquals("chill beats", orchestrator.context.getMediaTrack())
    }
}
