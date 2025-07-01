package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.feature.attribute.SpanAttribute
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class NodeExecuteSpan(
    tracer: Tracer,
    parentSpan: AgentRunSpan,
    private val runId: String,
    private val nodeName: String,
) : GenAIAgentSpan(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String): String =
            createIdFromParent(parentId = AgentRunSpan.createId(agentId, runId), nodeName = nodeName)

        private fun createIdFromParent(parentId: String, nodeName: String): String =
            "$parentId.node.$nodeName"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, nodeName)

    fun start() {
        val attributes = buildList {
            add(SpanAttribute.Operation.Name(SpanAttribute.Operation.OperationName.EXECUTE_NODE))
            add(SpanAttribute.Conversation.Id(runId))
            add(SpanAttribute.Custom("gen_ai.node.name", nodeName))
        }

        startInternal(kind = SpanKind.CLIENT, attributes = attributes)
    }

    fun end() {
        endInternal(emptyList(), StatusCode.OK)
    }
}
