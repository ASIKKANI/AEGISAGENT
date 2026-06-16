package com.edgeai.aegisagent.dsl

import com.edgeai.aegisagent.core.*
import com.edgeai.aegisagent.agents.*

/**
 * The central coordinator that routes user queries to target agents
 * and registers conversational state context.
 */
class AgentOrchestrator(
    val modelRunner: EdgeModelRunner,
    val context: AgentContext,
    val router: IntentRouterAgent,
    val agents: Map<String, Agent>
) {
    /**
     * Executes the orchestration pipeline:
     * 1. Clear turn traces.
     * 2. Route user request to target agent.
     * 3. Invoke target agent and run matched tools.
     * 4. Compile and return response description.
     */
    suspend fun processQuery(query: String): String {
        context.clearTrace()
        context.addTrace("--- NEW REQUEST RECEIVED ---")
        context.addTrace("Query: \"$query\"")

        // Add user query to conversation history
        context.history.add(Message("user", query))

        // 1. Router classifies intent
        context.addTrace("Routing query through IntentRouterAgent...")
        val targetAgentName = router.route(query, modelRunner)
        context.addTrace("Router classification output: '$targetAgentName'")

        // 2. Select target agent
        val agent = agents[targetAgentName]
        if (agent == null) {
            context.addTrace("Error: Target agent '$targetAgentName' is not configured.")
            val errMsg = "I'm sorry, I couldn't find a sub-agent to handle '$targetAgentName'."
            context.history.add(Message("assistant", errMsg))
            return errMsg
        }

        // 3. Execute agent task
        context.addTrace("Switching execution context to agent: [${agent.name}]")
        val result = agent.execute(query, context, modelRunner)
        
        // Add assistant result to conversation history
        context.history.add(Message("assistant", result))
        context.addTrace("--- REQUEST PROCESSING COMPLETE ---")
        
        return result
    }
}

// --- KOTLIN DSL BUILDERS ---

@DslMarker
annotation class OrchestratorDslMarker

@OrchestratorDslMarker
class OrchestratorBuilder {
    var modelRunner: EdgeModelRunner = SimulatedModelRunner()
    val context = AgentContext()
    
    private var routerAgent = IntentRouterAgent()
    private val agentMap = mutableMapOf<String, Agent>()

    fun router(init: IntentRouterBuilder.() -> Unit) {
        val builder = IntentRouterBuilder()
        builder.init()
        routerAgent = builder.build()
    }

    fun systemAgent(init: AgentBuilder.() -> Unit) {
        val builder = AgentBuilder("SystemControl")
        builder.init()
        val tools = builder.tools.map { it.build() }
        agentMap["system"] = SystemControlAgent(builder.systemPrompt, tools)
    }

    fun mediaAgent(init: AgentBuilder.() -> Unit) {
        val builder = AgentBuilder("MediaControl")
        builder.init()
        val tools = builder.tools.map { it.build() }
        agentMap["media"] = MediaControlAgent(builder.systemPrompt, tools)
    }

    fun cameraAgent(init: AgentBuilder.() -> Unit) {
        val builder = AgentBuilder("CameraExecutor")
        builder.init()
        val tools = builder.tools.map { it.build() }
        agentMap["camera"] = CameraAgent(builder.systemPrompt, tools)
    }

    fun productivityAgent(init: AgentBuilder.() -> Unit) {
        val builder = AgentBuilder("ProductivityScheduler")
        builder.init()
        val tools = builder.tools.map { it.build() }
        agentMap["productivity"] = ProductivityAgent(builder.systemPrompt, tools)
    }

    fun build(): AgentOrchestrator {
        return AgentOrchestrator(
            modelRunner = modelRunner,
            context = context,
            router = routerAgent,
            agents = agentMap
        )
    }
}

class IntentRouterBuilder {
    var systemPrompt: String = "You are the central intent router for AegisAgent. Classify the user instruction to one of: [route: system], [route: media], [route: camera], or [route: productivity]."
    
    fun build(): IntentRouterAgent {
        return IntentRouterAgent()
    }
}

@OrchestratorDslMarker
class AgentBuilder(val agentName: String) {
    var systemPrompt: String = ""
    val tools = mutableListOf<ToolBuilder>()

    fun tool(name: String, description: String, init: ToolBuilder.() -> Unit) {
        val builder = ToolBuilder(name, description)
        builder.init()
        tools.add(builder)
    }
}

@OrchestratorDslMarker
class ToolBuilder(val name: String, val description: String) {
    val parameters = mutableMapOf<String, String>()
    private var actionLambda: (Map<String, Any>, AgentContext) -> String = { _, _ -> "Executed" }

    fun parameter(name: String, typeDescription: String) {
        parameters[name] = typeDescription
    }

    fun onExecute(action: (Map<String, Any>, AgentContext) -> String) {
        actionLambda = action
    }

    fun build(): Tool {
        return Tool(name, description, parameters, actionLambda)
    }
}

/**
 * The entrypoint function to configure KOrchestra components using Kotlin DSL.
 */
fun agentOrchestrator(init: OrchestratorBuilder.() -> Unit): AgentOrchestrator {
    val builder = OrchestratorBuilder()
    builder.init()
    return builder.build()
}
