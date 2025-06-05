package ai.koog.agents.features.opentelemetry.feature

internal enum class SpanEvent(val id: String) {

    ROOT("root"),

    AGENT_RUN("agent.run"),
    STRATEGY_RUN("strategy.run"),
    NODE_EXECUTION("node.execution"),
    LLM_CALL("llm.call"),

    AGENT_RUN_START("agent.run.start"),
    AGENT_RUN_FINISH("agent.run.finish"),
    AGENT_RUN_ERROR("agent.run.error"),

    STRATEGY_RUN_START("strategy.run.start"),
    STRATEGY_RUN_FINISH("strategy.run.finish"),

    NODE_EXECUTION_START("node.execution.start"),
    NODE_EXECUTION_FINISH("node.execution.finish"),

    LLM_CALL_START("llm.call.start"),
    LLM_CALL_FINISH("llm.call.finish"),

    TOOL_CALL("tool.call"),
    TOOL_VALIDATION_ERROR("tool.validation.error"),
    TOOL_CALL_FAILURE("tool.call.failure"),
    TOOL_CALL_RESULT("tool.call.result");

    internal companion object {

        internal fun getAgentRunId(sessionId: String): String {
            return "${AGENT_RUN.id}-$sessionId"
        }

        internal fun getStrategyRunId(sessionId: String): String {
            return "${STRATEGY_RUN.id}-$sessionId"
        }

        internal fun getNodeExecutionId(sessionId: String, nodeId: String): String {
            return "${NODE_EXECUTION.id}-$sessionId-$nodeId"
        }

        internal fun getLLMCallId(sessionId: String, nodeId: String): String {
            return "${LLM_CALL.id}-$sessionId-$nodeId"
        }

        internal fun getToolCallId(sessionId: String, nodeId: String): String {
            return "${TOOL_CALL.id}-$sessionId-$nodeId"
        }

        fun getTopLayerId(): String {
            return ROOT.id
        }
    }

}
