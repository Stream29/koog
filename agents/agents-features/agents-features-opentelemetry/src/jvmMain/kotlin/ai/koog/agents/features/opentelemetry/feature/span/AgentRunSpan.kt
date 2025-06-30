package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class AgentRunSpan(
    tracer: Tracer,
    parentSpan: AgentSpan,
    val runId: String,
    val strategyName: String,
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
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.INVOKE_AGENT.id),
            GenAIAttribute.Agent.Id((parentSpan as AgentSpan).agentId),
            GenAIAttribute.Custom("gen_ai.agent.runId", runId),
            GenAIAttribute.Custom("gen_ai.agent.strategy", strategyName),
            GenAIAttribute.Custom("gen_ai.agent.completed", false),
        )

        return startInternal(attributes)
    }

    fun end(
        completed: Boolean,
        result: String,
        statusCode: StatusCode,
    ) {
        val attributes = listOf(
            GenAIAttribute.Custom("gen_ai.agent.result", result),
            GenAIAttribute.Custom("gen_ai.agent.completed", completed),
        )

        endInternal(attributes, statusCode)
    }
}
