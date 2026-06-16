package com.edgeai.aegisagent.core

/**
 * Metadata definition and executor closure for tools/functions that
 * local LLM agents can discover and run.
 */
class Tool(
    val name: String,
    val description: String,
    val parameters: Map<String, String>, // ParamName -> Type description (e.g., "state" -> "boolean")
    private val action: (Map<String, Any>, AgentContext) -> String
) {
    /**
     * Executes the tool's core logic with arguments mapped to system values,
     * mutating the device context and returning a status description.
     */
    fun execute(args: Map<String, Any>, context: AgentContext): String {
        return try {
            action(args, context)
        } catch (e: Exception) {
            "Error executing tool $name: ${e.message}"
        }
    }
}
