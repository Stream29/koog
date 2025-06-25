package ai.koog.agents.features.opentelemetry.feature

internal enum class OperationName(val id: String) {

    CHAT("chat"),
    CREATE_AGENT("create_agent"),
    EMBEDDINGS("embeddings"),
    EXECUTE_TOOL("execute_tool"),
    GENERATE_CONTENT("generate_content"),
    INVOKE_AGENT("invoke_agent"),
    TEXT_COMPLETION("text_completion"),

    RUN_STRATEGY("run_strategy"),
    EXECUTE_NODE("execute_node"),
}
