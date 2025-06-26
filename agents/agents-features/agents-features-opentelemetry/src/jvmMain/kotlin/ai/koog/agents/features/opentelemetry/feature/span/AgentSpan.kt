package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.SpanContextKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

internal class AgentSpan(
    tracer: Tracer,
    val agentId: String,
) : TraceSpanBase(tracer, null) {

    companion object {
        fun createId(agentId: String): String = "agent.$agentId"
    }

    val spanId = createId(agentId)

    override val spanEvent = SpanEvent.Agent(agentId = agentId)

    override val context: Context
        get() {
            parentSpan?.context?.with(SpanContextKey.KEY, span)
        }
        span.storeInContext(parentSpan?.context ?: Context.current())

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
