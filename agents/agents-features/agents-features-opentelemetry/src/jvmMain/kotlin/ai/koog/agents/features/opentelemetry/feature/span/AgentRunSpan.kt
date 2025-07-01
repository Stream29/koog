package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.feature.attribute.SpanAttribute
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class AgentRunSpan(
    tracer: Tracer,
    parentSpan: AgentSpan,
    private val runId: String,
    private val agentId: String,
    private val strategyName: String,
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, runId: String): String =
            createIdFromParent(parentId = AgentSpan.createId(agentId), runId = runId)

        private fun createIdFromParent(parentId: String, runId: String): String =
            "$parentId.run.$runId"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, runId)

    fun start(): Span {
        val attributes = listOf(
            SpanAttribute.Operation.Name(SpanAttribute.Operation.OperationName.INVOKE_AGENT),
            SpanAttribute.Agent.Id(agentId),
            SpanAttribute.Conversation.Id(runId),
            SpanAttribute.Custom("gen_ai.agent.strategy", strategyName),
            SpanAttribute.Custom("gen_ai.agent.completed", false),
        )

        return startInternal(kind = SpanKind.CLIENT, attributes = attributes)
    }

    fun end(
        completed: Boolean,
        result: String,
        statusCode: StatusCode,
    ) {
        val attributes = listOf(
            SpanAttribute.Custom("gen_ai.agent.result", result),
            SpanAttribute.Custom("gen_ai.agent.completed", completed),
        )

        endInternal(attributes, statusCode)
    }
}
