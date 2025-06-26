package ai.koog.agents.features.opentelemetry.feature.span

internal sealed interface SpanEvent {
    val id: String

    open class Agent(agentId: String) : SpanEvent {
        override val id: String = "agent.$agentId"
    }

    open class AgentRun(agentId: String, sessionId: String) : Agent(agentId) {
        override val id: String = "${super.id}.run.${sessionId}"
    }

    open class NodeExecution(agentId: String, sessionId: String, nodeName: String) : AgentRun(agentId, sessionId) {
        override val id: String = "${super.id}.node.${nodeName}"
    }

    class LLMCall(agentId: String, sessionId: String, nodeName: String) : NodeExecution(agentId, sessionId, nodeName) {
        override val id: String = "${super.id}.llm"
    }

    class ToolCall (agentId: String, sessionId: String, nodeName: String) : NodeExecution(agentId, sessionId, nodeName) {
        override val id: String = "${super.id}.tool"
    }
}
