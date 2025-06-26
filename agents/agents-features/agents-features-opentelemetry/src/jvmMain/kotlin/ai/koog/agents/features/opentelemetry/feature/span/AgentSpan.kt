package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Tracer

internal class AgentSpan(
    tracer: Tracer,
    val agentId: String,
) : TraceSpanBase(tracer, null) {

    companion object {
        fun createId(agentId: String): String = "agent.$agentId"
    }

    override val spanId = createId(agentId)

    fun start() {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.INVOKE_AGENT.id),
            GenAIAttribute.Agent.Id(agentId),
        )

        start(attributes)
    }

    fun end() {
        end(emptyList())
    }
}
