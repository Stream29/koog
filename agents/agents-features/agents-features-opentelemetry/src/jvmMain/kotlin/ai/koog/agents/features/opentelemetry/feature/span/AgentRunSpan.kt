package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer

internal class AgentRunSpan(
    tracer: Tracer,
    parentSpan: AgentSpan,
    val sessionId: String,
    val strategyName: String,
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, sessionId: String): String =
            createIdFromParent(parentId = AgentSpan.createId(agentId), sessionId = sessionId)

        private fun createIdFromParent(parentId: String, sessionId: String): String =
            "$parentId.run.$sessionId"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, sessionId)

    fun start(): Span {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.INVOKE_AGENT.id),
            GenAIAttribute.Agent.Id((parentSpan as AgentSpan).agentId),
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
