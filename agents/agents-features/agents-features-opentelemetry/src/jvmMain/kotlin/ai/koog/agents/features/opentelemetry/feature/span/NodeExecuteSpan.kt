package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class NodeExecuteSpan(
    tracer: Tracer,
    parentSpan: AgentRunSpan,
    private val runId: String,
    private val nodeName: String,
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String): String =
            createIdFromParent(parentId = AgentRunSpan.createId(agentId, runId), nodeName = nodeName)

        private fun createIdFromParent(parentId: String, nodeName: String): String =
            "$parentId.node.$nodeName"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, nodeName)

    fun start() {
        val attributes = buildList {
            add(GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.EXECUTE_NODE.id))
            add(GenAIAttribute.Conversation.Id(runId))
            add(GenAIAttribute.Custom("gen_ai.node.name", nodeName))
        }

        startInternal(kind = SpanKind.CLIENT, attributes = attributes)
    }

    fun end() {
        endInternal(emptyList(), StatusCode.OK)
    }
}
