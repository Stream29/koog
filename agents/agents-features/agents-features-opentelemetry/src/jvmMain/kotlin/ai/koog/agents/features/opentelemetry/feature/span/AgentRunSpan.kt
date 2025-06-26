package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

internal class AgentRunSpan(
    tracer: Tracer,
    override val parentSpan: AgentSpan,
    parentContext: Context,
) : TraceSpanBase(tracer, spanId, parentContext) {

    override val spanId: String
        get() = SpanEvent.AGENT_RUN

    fun start(
        sessionId: String,
        strategyName: String
    ): Span {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.INVOKE_AGENT.id),
            GenAIAttribute.Agent.Id(parentSpan.agentId),
            GenAIAttribute.Custom("gen_ai.agent.sessionId", sessionId),
            GenAIAttribute.Custom("gen_ai.agent.strategy", strategyName),
            GenAIAttribute.Custom("gen_ai.agent.completed", false),
        )

        return start(attributes)
    }

    fun end(
        completed: Boolean,
        result: String
    ) {
        val attributes = listOf(
            GenAIAttribute.Custom("gen_ai.agent.result", result),
            GenAIAttribute.Custom("gen_ai.agent.completed", completed),
        )

        end(attributes)
    }
}
