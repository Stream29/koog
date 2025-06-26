package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Tracer

internal class NodeExecuteSpan(
    tracer: Tracer,
    parentSpan: AgentRunSpan,
    val nodeName: String,
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, sessionId: String, nodeName: String): String =
            createIdFromParent(parentId = "agent.${agentId}.run${sessionId}", nodeName = nodeName)

        private fun createIdFromParent(parentId: String, nodeName: String): String =
            "$parentId.node.$nodeName"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, nodeName)

    fun start() {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.EXECUTE_NODE.id),
            GenAIAttribute.Custom("gen_ai.node.name", nodeName),
        )
        start(attributes)
    }

    fun end() {
        end(emptyList())
    }
}
