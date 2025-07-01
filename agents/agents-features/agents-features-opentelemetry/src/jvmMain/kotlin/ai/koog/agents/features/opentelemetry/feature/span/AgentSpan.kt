package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.feature.attribute.GenAIAttribute
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class AgentSpan(
    tracer: Tracer,
    private val agentId: String,
) : GenAIAgentSpan(tracer, null) {

    companion object {
        fun createId(agentId: String): String = "agent.$agentId"
    }

    override val spanId = createId(agentId)

    fun start() {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.INVOKE_AGENT),
            GenAIAttribute.Agent.Id(agentId),
        )

        startInternal(kind = SpanKind.CLIENT, attributes = attributes)
    }

    fun end() {
        endInternal(emptyList(), StatusCode.OK)
    }
}
