package ai.koog.agents.features.opentelemetry.feature

internal enum class EventName(val id: String) {

    AGENT("agent"),
    AGENT_BEFORE_START("agent.before.start"),
    AGENT_RUN_ERROR("agent.run.error"),
    AGENT_FINISHED("agent.finished"),

    STRATEGY_STARTED("strategy.started"),
    STRATEGY_FINISHED("strategy.finished"),

    NODE_EXECUTION_START("node.execution.start"),
    NODE_EXECUTION_END("node.execution.end"),

    LLM_CALL_START("llm.call.start"),
    LLM_CALL_END("llm.call.end"),

    TOOL_CALL("tool.call"),
    TOOL_VALIDATION_ERROR("tool.validation.error"),
    TOOL_CALL_FAILURE("tool.call.failure"),
    TOOL_CALL_RESULT("tool.call.result"),
}